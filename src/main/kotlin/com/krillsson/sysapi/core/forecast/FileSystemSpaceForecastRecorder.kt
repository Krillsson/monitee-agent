package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.metrics.Metrics
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Component
class FileSystemSpaceForecastRecorder(
    private val metrics: Metrics,
    private val historyRepository: HistoryRepository,
    private val forecastDAO: FileSystemSpaceForecastDAO,
    private val clock: Clock
) {

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.DAYS)
    @Transactional
    fun run() {
        val now = clock.instant()
        val from = now.minus(FORECAST_WINDOW_DAYS, ChronoUnit.DAYS)
        val history = historyRepository.getExtendedHistoryLimitedToDates(from, now)
        val fileSystems = metrics.fileSystemMetrics().fileSystems()

        val entities = fileSystems.mapNotNull { fileSystem ->
            val points = history.mapNotNull { entry ->
                entry.value.fileSystemLoads.firstOrNull { it.id == fileSystem.id }
                    ?.let { load -> entry.date to (fileSystem.totalSpaceBytes - load.freeSpaceBytes) }
            }
            FileSystemSpaceForecaster.forecast(points, fileSystem.totalSpaceBytes, now)
                ?.asEntity(fileSystem.id, now)
        }

        forecastDAO.deleteAllByFilesystemIdIn(fileSystems.map { it.id })
        forecastDAO.saveAll(entities)
    }

    companion object {
        private const val FORECAST_WINDOW_DAYS = 30L
    }
}
