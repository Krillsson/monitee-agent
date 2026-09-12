package com.krillsson.sysapi.core.history.series

import java.time.Instant

enum class HistoryResolution {
    AUTO,
    RAW,
    FIVE_MINUTE,
    HOURLY,
    DAILY
}

data class MetricHistory(
    val resolution: HistoryResolution,
    val from: Instant,
    val to: Instant,
    val points: List<MetricHistoryPoint>
)

data class MetricHistoryPoint(
    val timestamp: Instant,
    val samples: Int,
    val min: Double,
    val avg: Double,
    val max: Double,
    val last: Double
)

fun HistoryResolution.asMetricResolution(): MetricResolution? = when (this) {
    HistoryResolution.AUTO -> null
    HistoryResolution.RAW -> MetricResolution.RAW
    HistoryResolution.FIVE_MINUTE -> MetricResolution.FIVE_MINUTE
    HistoryResolution.HOURLY -> MetricResolution.HOURLY
    HistoryResolution.DAILY -> MetricResolution.DAILY
}

fun MetricResolution.asHistoryResolution(): HistoryResolution = when (this) {
    MetricResolution.RAW -> HistoryResolution.RAW
    MetricResolution.FIVE_MINUTE -> HistoryResolution.FIVE_MINUTE
    MetricResolution.HOURLY -> HistoryResolution.HOURLY
    MetricResolution.DAILY -> HistoryResolution.DAILY
}
