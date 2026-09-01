package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecast
import com.krillsson.sysapi.core.forecast.FileSystemSpaceForecaster
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.metrics.Metrics
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.stereotype.Controller
import java.time.Clock
import java.time.temporal.ChronoUnit

@Controller
class FileSystemResolver(
    val metrics: Metrics,
    val historyRepository: HistoryRepository,
    val clock: Clock
) {
    @BatchMapping(typeName = "FileSystem", field = "metrics")
    fun metrics(fileSystems: List<FileSystem>): Map<FileSystem, FileSystemLoad?> {
        val loadsById = metrics.fileSystemMetrics().fileSystemLoads().associateBy { it.id }
        return fileSystems.associateWith { loadsById[it.id] }
    }

    @BatchMapping(typeName = "FileSystem", field = "spaceForecast")
    fun spaceForecast(fileSystems: List<FileSystem>): Map<FileSystem, FileSystemSpaceForecast?> {
        val now = clock.instant()
        val from = now.minus(FORECAST_WINDOW_DAYS, ChronoUnit.DAYS)
        val history = historyRepository.getExtendedHistoryLimitedToDates(from, now)

        return fileSystems.associateWith { fileSystem ->
            val points = history.mapNotNull { entry ->
                entry.value.fileSystemLoads.firstOrNull { it.id == fileSystem.id }
                    ?.let { load -> entry.date to (fileSystem.totalSpaceBytes - load.freeSpaceBytes) }
            }
            FileSystemSpaceForecaster.forecast(points, fileSystem.totalSpaceBytes, now)
        }
    }

    companion object {
        private const val FORECAST_WINDOW_DAYS = 30L
    }
}