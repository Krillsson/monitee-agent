package com.krillsson.sysapi.core.domain.filesystem

import java.time.Instant

enum class FileSystemSpaceTrend {
    GROWING, SHRINKING, STABLE
}

data class FileSystemSpaceForecast(
    val trend: FileSystemSpaceTrend,
    val growthBytesPerDay: Double,
    val daysUntilFull: Double?,
    val daysUntilFullLow: Double?,
    val daysUntilFullHigh: Double?,
    val projectedFullDate: Instant?,
    val daysOfHistoryUsed: Double,
    val history: List<FileSystemSpaceForecastHistoryPoint>
)

data class FileSystemSpaceForecastHistoryPoint(val date: Instant, val usedBytes: Long)
