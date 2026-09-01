package com.krillsson.sysapi.core.domain.filesystem

import java.time.Instant

data class FileSystemSpaceForecast(
    val growthBytesPerDay: Double,
    val daysUntilFull: Double,
    val daysUntilFullLow: Double,
    val daysUntilFullHigh: Double,
    val projectedFullDate: Instant,
    val daysOfHistoryUsed: Double,
    val history: List<FileSystemSpaceForecastHistoryPoint>
)

data class FileSystemSpaceForecastHistoryPoint(val date: Instant, val usedBytes: Long)
