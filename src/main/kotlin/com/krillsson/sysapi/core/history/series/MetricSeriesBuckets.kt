package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.core.history.HistoryBuckets
import java.time.Instant
import java.time.ZoneId

data class MetricSeriesSummary(
    val samples: Int,
    val minValue: Double,
    val avgValue: Double,
    val maxValue: Double,
    val lastValue: Double
)

object MetricSeriesBuckets {

    fun startOf(instant: Instant, resolution: MetricResolution): Instant = when (resolution) {
        MetricResolution.RAW -> instant
        MetricResolution.FIVE_MINUTE -> HistoryBuckets.startOfFiveMinutes(instant)
        MetricResolution.HOURLY -> HistoryBuckets.startOfHour(instant)
        MetricResolution.DAILY -> HistoryBuckets.startOfDay(instant)
    }

    fun endOf(
        bucketStart: Instant,
        resolution: MetricResolution,
        zone: ZoneId = ZoneId.systemDefault()
    ): Instant = when (resolution) {
        MetricResolution.RAW -> bucketStart
        MetricResolution.FIVE_MINUTE -> HistoryBuckets.endOfFiveMinutes(bucketStart)
        MetricResolution.HOURLY -> HistoryBuckets.endOfHour(bucketStart, zone)
        MetricResolution.DAILY -> HistoryBuckets.endOfDay(bucketStart, zone)
    }

    fun below(resolution: MetricResolution): MetricResolution? = when (resolution) {
        MetricResolution.RAW -> null
        MetricResolution.FIVE_MINUTE -> MetricResolution.RAW
        MetricResolution.HOURLY -> MetricResolution.FIVE_MINUTE
        MetricResolution.DAILY -> MetricResolution.HOURLY
    }

    fun merge(buckets: List<MetricSeriesBucketEntity>): MetricSeriesSummary {
        val samples = buckets.sumOf { it.samples }
        return MetricSeriesSummary(
            samples = samples,
            minValue = buckets.minOf { it.minValue },
            avgValue = if (samples == 0) {
                buckets.map { it.avgValue }.average()
            } else {
                buckets.sumOf { it.avgValue * it.samples } / samples
            },
            maxValue = buckets.maxOf { it.maxValue },
            lastValue = buckets.maxBy { it.bucketStart }.lastValue
        )
    }
}
