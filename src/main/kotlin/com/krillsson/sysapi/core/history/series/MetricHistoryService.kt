package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.config.RetentionConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class MetricHistoryService(
    private val repository: MetricSeriesBucketRepository,
    yamlConfigFile: YAMLConfigFile
) {
    private val retention = yamlConfigFile.metricsConfig.history.series

    private val tiers = listOf(
        Tier(MetricResolution.RAW, Duration.ofHours(6), retention.raw),
        Tier(MetricResolution.FIVE_MINUTE, Duration.ofHours(48), retention.fiveMinute),
        Tier(MetricResolution.HOURLY, Duration.ofDays(30), retention.hourly)
    )

    fun history(
        metric: MetricId,
        itemId: String,
        from: Instant,
        to: Instant,
        requested: HistoryResolution?
    ): MetricHistory {
        val resolution = resolutionFor(from, to, requested)
        return MetricHistory(
            resolution = resolution.asHistoryResolution(),
            from = from,
            to = to,
            points = pointsAt(metric, itemId, resolution, from, to)
        )
    }

    fun tieredPoints(metric: MetricId, itemId: String, from: Instant, to: Instant): List<MetricHistoryPoint> {
        var cursor = from
        val points = mutableListOf<MetricHistoryPoint>()
        listOf(MetricResolution.DAILY, MetricResolution.HOURLY, MetricResolution.FIVE_MINUTE).forEach { resolution ->
            val buckets = buckets(metric, itemId, resolution, cursor, to)
            points += buckets.map { it.asPoint() }
            buckets.lastOrNull()?.let {
                cursor = MetricSeriesBuckets.endOf(it.bucketStart, resolution).coerceAtLeast(cursor)
            }
        }
        points += buckets(metric, itemId, MetricResolution.RAW, cursor, to).map { it.asPoint() }
        return points
    }

    fun rawWindowStart(now: Instant): Instant = now.minus(retention.raw.olderThan, retention.raw.unit)

    // Whisper's archive rule: a range reaching further back than a tier keeps is served by a
    // coarser tier, rather than truncated to the part that tier still happens to hold.
    fun resolutionFor(from: Instant, to: Instant, requested: HistoryResolution? = null): MetricResolution {
        val floor = requested?.asMetricResolution()
        val range = Duration.between(from, to)
        val now = Instant.now()
        return tiers
            .dropWhile { floor != null && it.resolution != floor }
            .firstOrNull { tier ->
                tier.covers(from, now) && (floor != null || range <= tier.autoRangeLimit)
            }
            ?.resolution
            ?: MetricResolution.DAILY
    }

    fun availability(): List<MetricTierAvailability> {
        val now = Instant.now()
        return (tiers.map { it.resolution to it.retainedFrom(now) } +
            (MetricResolution.DAILY to retainedFrom(retention.daily, now)))
            .map { (resolution, retainedFrom) ->
                MetricTierAvailability(
                    resolution = resolution.asHistoryResolution(),
                    retainedFrom = retainedFrom,
                    earliestPoint = repository
                        .findFirstByResolutionOrderByBucketStartAsc(resolution)
                        .orElse(null)
                        ?.bucketStart,
                    latestPoint = repository
                        .findFirstByResolutionOrderByBucketStartDesc(resolution)
                        .orElse(null)
                        ?.bucketStart
                )
            }
    }

    private fun pointsAt(
        metric: MetricId,
        itemId: String,
        resolution: MetricResolution,
        from: Instant,
        to: Instant
    ): List<MetricHistoryPoint> = when (resolution) {
        MetricResolution.RAW -> buckets(metric, itemId, MetricResolution.RAW, from, to).map { it.asPoint() }
        else -> bucketPoints(metric, itemId, resolution, from, to)
    }

    private fun bucketPoints(
        metric: MetricId,
        itemId: String,
        resolution: MetricResolution,
        from: Instant,
        to: Instant
    ): List<MetricHistoryPoint> {
        val stored = buckets(metric, itemId, resolution, from, to)
        val tailFrom = stored.lastOrNull()
            ?.let { MetricSeriesBuckets.endOf(it.bucketStart, resolution).coerceAtLeast(from) }
            ?: from
        if (!tailFrom.isBefore(to)) {
            return stored.map { it.asPoint() }
        }
        val tail = tieredPoints(metric, itemId, tailFrom, to)
            .groupBy { MetricSeriesBuckets.startOf(it.timestamp, resolution) }
            .toSortedMap()
            .map { (bucketStart, points) -> points.mergedAt(bucketStart) }
        return stored.map { it.asPoint() } + tail
    }

    private fun buckets(
        metric: MetricId,
        itemId: String,
        resolution: MetricResolution,
        from: Instant,
        to: Instant
    ): List<MetricSeriesBucketEntity> {
        if (!from.isBefore(to)) {
            return emptyList()
        }
        return repository
            .findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                metric,
                itemId,
                resolution,
                from,
                to
            )
    }

    private fun List<MetricHistoryPoint>.mergedAt(timestamp: Instant): MetricHistoryPoint {
        val samples = sumOf { it.samples }
        return MetricHistoryPoint(
            timestamp = timestamp,
            samples = samples,
            min = minOf { it.min },
            avg = if (samples == 0) map { it.avg }.average() else sumOf { it.avg * it.samples } / samples,
            max = maxOf { it.max },
            last = maxBy { it.timestamp }.last
        )
    }

    private fun MetricSeriesBucketEntity.asPoint() = MetricHistoryPoint(
        timestamp = bucketStart,
        samples = samples,
        min = minValue,
        avg = avgValue,
        max = maxValue,
        last = lastValue
    )

    private fun Tier.covers(from: Instant, now: Instant): Boolean = !from.isBefore(retainedFrom(now))

    private fun Tier.retainedFrom(now: Instant): Instant = retainedFrom(retention, now)

    private fun retainedFrom(retention: RetentionConfiguration, now: Instant): Instant =
        now.minus(retention.olderThan, retention.unit)

    private data class Tier(
        val resolution: MetricResolution,
        val autoRangeLimit: Duration,
        val retention: RetentionConfiguration
    )
}
