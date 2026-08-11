package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.round
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.*

@Component
class CheckHistoryService(
    private val resultRepository: CheckResultRepository,
    private val bucketRepository: CheckResultBucketRepository,
    yamlConfigFile: YAMLConfigFile
) {
    companion object {
        private val RAW_RANGE_LIMIT: Duration = Duration.ofHours(6)
        private val HOURLY_RANGE_LIMIT: Duration = Duration.ofDays(30)
    }

    private val retention = yamlConfigFile.metricsConfig.history.checks

    fun history(checkId: UUID, from: Instant, to: Instant, requested: CheckResolution?): CheckHistory {
        val resolution = when (requested) {
            null, CheckResolution.AUTO -> resolutionFor(from, to)
            else -> requested
        }
        val now = Instant.now()
        val points = when (resolution) {
            CheckResolution.RAW -> rawPoints(checkId, from, to, now)
            CheckResolution.HOURLY -> bucketPoints(checkId, BucketResolution.HOURLY, from, to, now)
            CheckResolution.DAILY -> bucketPoints(checkId, BucketResolution.DAILY, from, to, now)
            CheckResolution.AUTO -> emptyList()
        }
        return CheckHistory(resolution, from, to, points)
    }

    fun uptime(checkId: UUID, from: Instant, to: Instant): CheckUptime {
        val now = Instant.now()
        val points = tieredPoints(checkId, from, to, now)
        val samples = points.sumOf { it.samples }
        val successful = points.sumOf { it.successful }
        val downtimeSeconds = points.sumOf { it.downtimeSeconds }
        val totalSeconds = Duration.between(from, minOf(to, now)).seconds.coerceAtLeast(0)
        return CheckUptime(
            from = from,
            to = to,
            samples = samples,
            successful = successful,
            failed = samples - successful,
            uptimePercent = uptimePercent(totalSeconds, downtimeSeconds),
            downtimeSeconds = downtimeSeconds,
            totalSeconds = totalSeconds
        )
    }

    fun uptimeMetrics(checkId: UUID, from: Instant, to: Instant): UptimeMetrics {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val days = tieredPoints(checkId, from, to, now)
            .groupBy { CheckBuckets.startOfDay(it.timestamp, zone) }
            .toSortedMap()
            .map { (dayStart, points) ->
                val dayEnd = minOf(CheckBuckets.endOf(dayStart, BucketResolution.DAILY, zone), now)
                val secondsElapsed = Duration.between(dayStart, dayEnd).seconds.coerceAtLeast(0)
                val downtimeSeconds = points.sumOf { it.downtimeSeconds }
                UptimeDay(
                    timestampAtStartOfDay = dayStart,
                    uptimePercent = downtimePercent(secondsElapsed, downtimeSeconds),
                    downTimeSeconds = downtimeSeconds,
                    totalSeconds = secondsElapsed
                )
            }
        return UptimeMetrics(perDay = days, total = days.asTotal(now))
    }

    fun resultsBetween(checkId: UUID, from: Instant, to: Instant, limit: Int?): List<CheckResult> {
        val results = resultRepository
            .findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(checkId, from, to)
            .map { it.asDomain() }
        return if (limit != null && results.size > limit) results.takeLast(limit) else results
    }

    private fun resolutionFor(from: Instant, to: Instant): CheckResolution {
        val range = Duration.between(from, to)
        val rawWindowStart = Instant.now().minus(retention.raw.olderThan, retention.raw.unit)
        return when {
            range <= RAW_RANGE_LIMIT && !from.isBefore(rawWindowStart) -> CheckResolution.RAW
            range <= HOURLY_RANGE_LIMIT -> CheckResolution.HOURLY
            else -> CheckResolution.DAILY
        }
    }

    private fun tieredPoints(checkId: UUID, from: Instant, to: Instant, now: Instant): List<CheckHistoryPoint> {
        val daily = buckets(checkId, BucketResolution.DAILY, from, to)
        val hourlyFrom = daily.lastOrNull()
            ?.let { CheckBuckets.endOf(it.bucketStart, BucketResolution.DAILY) }
            ?.coerceAtLeast(from)
            ?: from
        val hourly = buckets(checkId, BucketResolution.HOURLY, hourlyFrom, to)
        val rawFrom = hourly.lastOrNull()
            ?.let { CheckBuckets.endOf(it.bucketStart, BucketResolution.HOURLY) }
            ?.coerceAtLeast(hourlyFrom)
            ?: hourlyFrom
        return daily.map { it.asPoint() } + hourly.map { it.asPoint() } + rawPoints(checkId, rawFrom, to, now)
    }

    private fun buckets(
        checkId: UUID,
        resolution: BucketResolution,
        from: Instant,
        to: Instant
    ): List<CheckResultBucketEntity> = bucketRepository
        .findByCheckIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
            checkId,
            resolution,
            from,
            to
        )

    private fun bucketPoints(
        checkId: UUID,
        resolution: BucketResolution,
        from: Instant,
        to: Instant,
        now: Instant
    ): List<CheckHistoryPoint> {
        val stored = buckets(checkId, resolution, from, to)
        val tailFrom = stored.lastOrNull()
            ?.let { CheckBuckets.endOf(it.bucketStart, resolution) }
            ?.coerceAtLeast(from)
            ?: from
        if (!tailFrom.isBefore(to)) {
            return stored.map { it.asPoint() }
        }
        val tail = tieredPoints(checkId, tailFrom, to, now)
            .groupBy { CheckBuckets.startOf(it.timestamp, resolution) }
            .toSortedMap()
            .map { (bucketStart, points) -> points.mergedAt(bucketStart) }
        return stored.map { it.asPoint() } + tail
    }

    private fun List<CheckHistoryPoint>.mergedAt(timestamp: Instant): CheckHistoryPoint {
        val samples = sumOf { it.samples }
        val successful = sumOf { it.successful }
        return CheckHistoryPoint(
            timestamp = timestamp,
            samples = samples,
            successful = successful,
            failed = sumOf { it.failed },
            uptimePercent = if (samples == 0) 0.0 else (100.0 * successful / samples).round(4),
            downtimeSeconds = sumOf { it.downtimeSeconds },
            minLatencyMs = minOf { it.minLatencyMs },
            avgLatencyMs = if (samples == 0) 0 else sumOf { it.avgLatencyMs * it.samples } / samples,
            maxLatencyMs = maxOf { it.maxLatencyMs },
            p95LatencyMs = maxOf { it.p95LatencyMs }
        )
    }

    private fun rawPoints(checkId: UUID, from: Instant, to: Instant, now: Instant): List<CheckHistoryPoint> {
        if (!from.isBefore(to)) {
            return emptyList()
        }
        val results = resultRepository
            .findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(checkId, from, to)
        return results.mapIndexed { index, result ->
            val until = results.getOrNull(index + 1)?.timestamp ?: minOf(to, now)
            val downtimeSeconds =
                if (result.successful) 0 else Duration.between(result.timestamp, until).seconds.coerceAtLeast(0)
            CheckHistoryPoint(
                timestamp = result.timestamp,
                samples = 1,
                successful = if (result.successful) 1 else 0,
                failed = if (result.successful) 0 else 1,
                uptimePercent = if (result.successful) 100.0 else 0.0,
                downtimeSeconds = downtimeSeconds,
                minLatencyMs = result.latencyMs,
                avgLatencyMs = result.latencyMs,
                maxLatencyMs = result.latencyMs,
                p95LatencyMs = result.latencyMs
            )
        }
    }

    private fun CheckResultBucketEntity.asPoint() = CheckHistoryPoint(
        timestamp = bucketStart,
        samples = samples,
        successful = successful,
        failed = failed,
        uptimePercent = if (samples == 0) 0.0 else (100.0 * successful / samples).round(4),
        downtimeSeconds = downtimeSeconds,
        minLatencyMs = minLatencyMs,
        avgLatencyMs = avgLatencyMs,
        maxLatencyMs = maxLatencyMs,
        p95LatencyMs = p95LatencyMs
    )

    private fun List<UptimeDay>.asTotal(now: Instant): UptimePeriod {
        if (isEmpty()) {
            return UptimePeriod(now, now, 0, 0.0, 0)
        }
        val totalDownTimeSeconds = sumOf { it.downTimeSeconds }
        val totalSeconds = sumOf { it.totalSeconds }
        return UptimePeriod(
            periodStart = first().timestampAtStartOfDay,
            periodEnd = last().timestampAtStartOfDay,
            totalDownTimeSeconds = totalDownTimeSeconds,
            totalUptimePercent = downtimePercent(totalSeconds, totalDownTimeSeconds),
            totalSeconds = totalSeconds
        )
    }

    private fun downtimePercent(totalSeconds: Long, downtimeSeconds: Long): Double =
        if (totalSeconds <= 0) 0.0 else (100.0 * downtimeSeconds / totalSeconds).round(4)

    private fun uptimePercent(totalSeconds: Long, downtimeSeconds: Long): Double =
        (100.0 - downtimePercent(totalSeconds, downtimeSeconds)).coerceIn(0.0, 100.0).round(4)
}

fun CheckResultEntity.asDomain() = CheckResult(
    id = id,
    checkId = checkId,
    checkType = checkType,
    timestamp = timestamp,
    successful = successful,
    latencyMs = latencyMs,
    message = message,
    responseCode = responseCode,
    errorBody = errorBody,
    resolvedValues = resolvedValues
)
