package com.krillsson.sysapi.ups

import com.krillsson.sysapi.core.history.db.UpsMetricsHistoryDAO
import com.krillsson.sysapi.core.history.db.asUpsDeviceMetrics
import com.krillsson.sysapi.core.history.db.asUpsMetricsHistoryEntity
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.measureTimeMillis
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class UpsMetricsHistoryRepository(
    private val clock: Clock,
    private val upsMetricsHistoryDAO: UpsMetricsHistoryDAO
) {
    val logger by logger()

    fun getHistoryLimitedToDates(
        upsId: String,
        fromDate: Instant,
        toDate: Instant
    ): List<UpsMetricsHistoryEntry> {
        val result = measureTimeMillis {
            upsMetricsHistoryDAO.findAllBetween(upsId, fromDate, toDate)
        }
        logger.info(
            "Took {} to fetch {} UPS metrics history entries",
            "${result.first.toInt()}ms",
            result.second.size
        )
        return result.second.map {
            UpsMetricsHistoryEntry(
                id = it.upsId,
                timestamp = it.timestamp,
                metrics = it.asUpsDeviceMetrics()
            )

        }
    }

    fun recordUpsMetrics(metrics: List<UpsDevice.Metrics>) {
        val now = clock.instant()
        val entries = metrics.map { it.asUpsMetricsHistoryEntity(it.id, now) }
        upsMetricsHistoryDAO.insertAll(entries)
    }

    fun purgeUpsMetrics(olderThan: Long, unit: ChronoUnit) {
        val maxAge = clock.instant().minus(olderThan, unit)
        val deletedCount = upsMetricsHistoryDAO.purge(maxAge)
        logger.info("Purged $deletedCount UPS metrics history older than {}", maxAge)
    }
}