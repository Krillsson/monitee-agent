package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecast
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecastHistoryPoint
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceTrend
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Entity
class FileSystemSpaceForecastEntity(
    @Id
    val filesystemId: String,
    val computedAt: Instant,
    @Enumerated(EnumType.STRING)
    val trend: FileSystemSpaceTrend,
    val growthBytesPerDay: Double,
    val daysUntilFull: Double?,
    val daysUntilFullLow: Double?,
    val daysUntilFullHigh: Double?,
    val projectedFullDate: Instant?,
    val daysOfHistoryUsed: Double,
    @Convert(converter = FileSystemSpaceForecastHistoryJsonConverter::class)
    val history: List<FileSystemSpaceForecastHistoryPoint>
)

@Repository
interface FileSystemSpaceForecastDAO : JpaRepository<FileSystemSpaceForecastEntity, String> {
    fun deleteAllByFilesystemIdIn(ids: List<String>)
}

fun FileSystemSpaceForecast.asEntity(filesystemId: String, computedAt: Instant) = FileSystemSpaceForecastEntity(
    filesystemId = filesystemId,
    computedAt = computedAt,
    trend = trend,
    growthBytesPerDay = growthBytesPerDay,
    daysUntilFull = daysUntilFull,
    daysUntilFullLow = daysUntilFullLow,
    daysUntilFullHigh = daysUntilFullHigh,
    projectedFullDate = projectedFullDate,
    daysOfHistoryUsed = daysOfHistoryUsed,
    history = history
)

fun FileSystemSpaceForecastEntity.asDomain() = FileSystemSpaceForecast(
    trend = trend,
    growthBytesPerDay = growthBytesPerDay,
    daysUntilFull = daysUntilFull,
    daysUntilFullLow = daysUntilFullLow,
    daysUntilFullHigh = daysUntilFullHigh,
    projectedFullDate = projectedFullDate,
    daysOfHistoryUsed = daysOfHistoryUsed,
    history = history
)
