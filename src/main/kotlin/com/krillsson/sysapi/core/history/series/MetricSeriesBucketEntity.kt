package com.krillsson.sysapi.core.history.series

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.Instant
import java.util.UUID

enum class MetricResolution {
    RAW,
    FIVE_MINUTE,
    HOURLY,
    DAILY
}

data class MetricSample(
    val metric: MetricId,
    val itemId: String,
    val timestamp: Instant,
    val value: Double
)

@Entity
class MetricSeriesBucketEntity(
    @Id
    val id: UUID,
    @Enumerated(EnumType.STRING)
    val metric: MetricId,
    val itemId: String,
    @Enumerated(EnumType.STRING)
    val resolution: MetricResolution,
    val bucketStart: Instant,
    val samples: Int,
    val minValue: Double,
    val avgValue: Double,
    val maxValue: Double,
    val lastValue: Double
)
