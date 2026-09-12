package com.krillsson.sysapi.core.history.compat

import com.krillsson.sysapi.core.domain.docker.BlockIOUsage
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.docker.ContainerMetricsHistoryEntry
import com.krillsson.sysapi.core.domain.docker.CpuUsage
import com.krillsson.sysapi.core.domain.docker.MemoryUsage
import com.krillsson.sysapi.core.domain.docker.NetworkUsage
import com.krillsson.sysapi.core.domain.docker.ThrottlingData
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.series.MetricId
import com.krillsson.sysapi.core.history.series.MetricResolution
import com.krillsson.sysapi.core.history.series.MetricSeriesBucketEntity
import com.krillsson.sysapi.core.history.series.MetricSeriesBuckets
import com.krillsson.sysapi.core.history.series.MetricSeriesBucketRepository
import com.krillsson.sysapi.docker.ContainersHistoryRepository
import com.krillsson.sysapi.ups.UpsDevice
import com.krillsson.sysapi.ups.UpsMetricsHistoryEntry
import com.krillsson.sysapi.ups.UpsMetricsHistoryRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class LegacyHistoryCompatService(
    private val historyRepository: HistoryRepository,
    private val containersHistoryRepository: ContainersHistoryRepository,
    private val upsMetricsHistoryRepository: UpsMetricsHistoryRepository,
    private val seriesRepository: MetricSeriesBucketRepository
) {

    fun systemHistory(from: Instant, to: Instant): List<SystemHistoryPoint> {
        val stored = historyRepository.getHistoryLimitedToDates(from, to)
        val storedFrom = stored.minOfOrNull { it.date } ?: to
        return synthesizedSnapshots(from, storedFrom)
            .map { (timestamp, snapshot) -> SeriesEntryBuilder.systemPoint(timestamp, snapshot) } +
            stored.map { SystemHistoryPoint.Stored(it) }
    }

    fun containerHistory(containerId: String, from: Instant, to: Instant): List<ContainerMetricsHistoryEntry> {
        val stored = containersHistoryRepository.getHistoryLimitedToDates(containerId, from, to)
        val storedFrom = stored.minOfOrNull { it.timestamp } ?: to
        return synthesizedSnapshots(from, storedFrom)
            .filter { (_, snapshot) -> snapshot.flag(MetricId.CONTAINER_RUNNING, containerId) == true }
            .map { (timestamp, snapshot) ->
                ContainerMetricsHistoryEntry(
                    containerId = containerId,
                    timestamp = timestamp,
                    metrics = snapshot.containerMetrics(containerId)
                )
            } + stored
    }

    fun upsHistory(id: String, from: Instant, to: Instant): List<UpsMetricsHistoryEntry> {
        val stored = upsMetricsHistoryRepository.getHistoryLimitedToDates(id, from, to)
        val storedFrom = stored.minOfOrNull { it.timestamp } ?: to
        return synthesizedSnapshots(from, storedFrom)
            .mapNotNull { (timestamp, snapshot) ->
                val operatingNormally = snapshot.flag(MetricId.UPS_OPERATING_NORMALLY, id)
                    ?: return@mapNotNull null
                UpsMetricsHistoryEntry(
                    id = id,
                    timestamp = timestamp,
                    metrics = snapshot.upsMetrics(id, operatingNormally)
                )
            } + stored
    }

    private fun synthesizedSnapshots(from: Instant, to: Instant): List<Pair<Instant, SeriesSnapshot>> {
        if (!from.isBefore(to)) {
            return emptyList()
        }
        var cursor = from
        val byTimestamp = sortedMapOf<Instant, List<MetricSeriesBucketEntity>>()
        for (resolution in SYNTHESIS_TIERS) {
            if (!cursor.isBefore(to)) {
                break
            }
            val buckets = seriesRepository
                .findByResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                    resolution,
                    cursor,
                    to
                )
            if (buckets.isEmpty()) {
                continue
            }
            byTimestamp.putAll(buckets.groupBy { it.bucketStart })
            cursor = MetricSeriesBuckets
                .endOf(buckets.maxOf { it.bucketStart }, resolution)
                .coerceAtLeast(cursor)
        }
        return byTimestamp.map { (timestamp, buckets) -> timestamp to SeriesSnapshot(buckets) }
    }

    private fun SeriesSnapshot.containerMetrics(containerId: String) = ContainerMetrics(
        id = containerId,
        cpuUsage = CpuUsage(
            usagePercentPerCore = 0.0,
            usagePercentTotal = value(MetricId.CONTAINER_CPU_PERCENT, containerId) ?: 0.0,
            throttlingData = ThrottlingData(0, 0, 0)
        ),
        memoryUsage = MemoryUsage(
            usageBytes = value(MetricId.CONTAINER_MEMORY_USED_BYTES, containerId)?.toLong() ?: 0L,
            usagePercent = value(MetricId.CONTAINER_MEMORY_USED_PERCENT, containerId) ?: 0.0,
            limitBytes = value(MetricId.CONTAINER_MEMORY_LIMIT_BYTES, containerId)?.toLong() ?: 0L
        ),
        currentPid = 0,
        networkUsage = NetworkUsage(
            bytesReceived = value(MetricId.CONTAINER_RX_BYTES, containerId)?.toLong() ?: 0L,
            bytesTransferred = value(MetricId.CONTAINER_TX_BYTES, containerId)?.toLong() ?: 0L
        ),
        blockIOUsage = BlockIOUsage(
            bytesWritten = value(MetricId.CONTAINER_BLOCK_WRITE_BYTES, containerId)?.toLong() ?: 0L,
            bytesRead = value(MetricId.CONTAINER_BLOCK_READ_BYTES, containerId)?.toLong() ?: 0L
        )
    )

    private fun SeriesSnapshot.upsMetrics(id: String, operatingNormally: Boolean) = UpsDevice.Metrics(
        id = id,
        batteryMetrics = UpsDevice.Metrics.BatteryMetrics(
            capacity = null,
            chargePercent = value(MetricId.UPS_BATTERY_CHARGE_PERCENT, id)?.toInt(),
            runtime = null,
            voltage = value(MetricId.UPS_BATTERY_VOLTAGE, id)?.toFloat(),
            voltageNominal = null,
            chargerStatus = null
        ),
        inputMetrics = UpsDevice.Metrics.InputMetrics(
            current = null,
            frequency = null,
            voltage = value(MetricId.UPS_INPUT_VOLTAGE, id)?.toFloat()
        ),
        outputMetrics = UpsDevice.Metrics.OutputMetrics(
            current = null,
            frequency = null,
            powerFactor = null,
            voltage = value(MetricId.UPS_OUTPUT_VOLTAGE, id)?.toFloat()
        ),
        loadPercent = value(MetricId.UPS_LOAD_PERCENT, id)?.toInt(),
        realPowerLoadWatts = value(MetricId.UPS_LOAD_WATTS, id)?.toInt(),
        powerLoadVA = value(MetricId.UPS_LOAD_VA, id)?.toInt(),
        upsStatus = if (operatingNormally) listOf(UpsDevice.Status.OnLine) else listOf(UpsDevice.Status.Unknown)
    )

    companion object {
        private val SYNTHESIS_TIERS = listOf(
            MetricResolution.DAILY,
            MetricResolution.HOURLY,
            MetricResolution.FIVE_MINUTE
        )
    }
}
