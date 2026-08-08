package com.krillsson.sysapi.core.check

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong

object CheckBuckets {

    fun startOfHour(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        instant.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()

    fun startOfDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    fun endOf(bucketStart: Instant, resolution: BucketResolution, zone: ZoneId = ZoneId.systemDefault()): Instant =
        when (resolution) {
            BucketResolution.HOURLY -> bucketStart.atZone(zone).plusHours(1).toInstant()
            BucketResolution.DAILY -> bucketStart.atZone(zone).plusDays(1).toInstant()
        }

    fun startOf(instant: Instant, resolution: BucketResolution): Instant = when (resolution) {
        BucketResolution.HOURLY -> startOfHour(instant)
        BucketResolution.DAILY -> startOfDay(instant)
    }

    fun summarize(
        results: List<CheckResultEntity>,
        windowStart: Instant,
        windowEnd: Instant,
        now: Instant,
        precedingResult: CheckResultEntity?
    ): BucketSummary {
        val latencies = results.map { it.latencyMs }.sorted()
        return BucketSummary(
            samples = results.size,
            successful = results.count { it.successful },
            failed = results.count { !it.successful },
            downtimeSeconds = downtimeSeconds(results, windowStart, windowEnd, now, precedingResult),
            minLatencyMs = latencies.first(),
            avgLatencyMs = latencies.average().roundToLong(),
            maxLatencyMs = latencies.last(),
            p95LatencyMs = latencies.percentile(0.95),
            lastMessage = results.last().message
        )
    }

    fun merge(buckets: List<CheckResultBucketEntity>): BucketSummary {
        val samples = buckets.sumOf { it.samples }
        return BucketSummary(
            samples = samples,
            successful = buckets.sumOf { it.successful },
            failed = buckets.sumOf { it.failed },
            downtimeSeconds = buckets.sumOf { it.downtimeSeconds },
            minLatencyMs = buckets.minOf { it.minLatencyMs },
            avgLatencyMs = if (samples == 0) 0 else buckets.sumOf { it.avgLatencyMs * it.samples } / samples,
            maxLatencyMs = buckets.maxOf { it.maxLatencyMs },
            p95LatencyMs = buckets.maxOf { it.p95LatencyMs },
            lastMessage = buckets.last().lastMessage
        )
    }

    private fun downtimeSeconds(
        results: List<CheckResultEntity>,
        windowStart: Instant,
        windowEnd: Instant,
        now: Instant,
        precedingResult: CheckResultEntity?
    ): Long {
        var downtime = 0L
        if (precedingResult != null && !precedingResult.successful) {
            downtime += Duration.between(windowStart, results.first().timestamp).seconds.coerceAtLeast(0)
        }
        results.windowed(2).forEach { (earlier, later) ->
            if (!earlier.successful) {
                downtime += Duration.between(earlier.timestamp, later.timestamp).seconds
            }
        }
        val last = results.last()
        if (!last.successful) {
            downtime += Duration.between(last.timestamp, minOf(windowEnd, now)).seconds.coerceAtLeast(0)
        }
        return downtime
    }

    private fun List<Long>.percentile(fraction: Double): Long {
        val rank = ceil(fraction * size).toInt().coerceIn(1, size)
        return this[rank - 1]
    }
}

data class BucketSummary(
    val samples: Int,
    val successful: Int,
    val failed: Int,
    val downtimeSeconds: Long,
    val minLatencyMs: Long,
    val avgLatencyMs: Long,
    val maxLatencyMs: Long,
    val p95LatencyMs: Long,
    val lastMessage: String?
)
