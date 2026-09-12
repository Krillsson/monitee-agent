package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class MetricHistoryService(
    private val repository: MetricSeriesBucketRepository,
    yamlConfigFile: YAMLConfigFile
) {
    companion object {
        private val RAW_RANGE_LIMIT: Duration = Duration.ofHours(6)
        private val FIVE_MINUTE_RANGE_LIMIT: Duration = Duration.ofHours(48)
        private val HOURLY_RANGE_LIMIT: Duration = Duration.ofDays(30)
    }

    private val retention = yamlConfigFile.metricsConfig.history.series

    fun history(
        metric: MetricId,
        itemId: String,
        from: Instant,
        to: Instant,
        requested: HistoryResolution?
    ): MetricHistory {
        val resolution = requested?.asMetricResolution() ?: resolutionFor(from, to)
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

    fun resolutionFor(from: Instant, to: Instant): MetricResolution {
        val range = Duration.between(from, to)
        val now = Instant.now()
        val rawWindowStart = now.minus(retention.raw.olderThan, retention.raw.unit)
        val fiveMinuteWindowStart = now.minus(retention.fiveMinute.olderThan, retention.fiveMinute.unit)
        return when {
            range <= RAW_RANGE_LIMIT && !from.isBefore(rawWindowStart) -> MetricResolution.RAW
            range <= FIVE_MINUTE_RANGE_LIMIT && !from.isBefore(fiveMinuteWindowStart) -> MetricResolution.FIVE_MINUTE
            range <= HOURLY_RANGE_LIMIT -> MetricResolution.HOURLY
            else -> MetricResolution.DAILY
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
}
