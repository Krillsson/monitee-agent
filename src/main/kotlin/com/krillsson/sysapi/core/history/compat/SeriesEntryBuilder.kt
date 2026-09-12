package com.krillsson.sysapi.core.history.compat

import com.krillsson.sysapi.core.domain.cpu.CpuHealth
import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.cpu.LoadAverages
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.disk.DiskSpeed
import com.krillsson.sysapi.core.domain.disk.DiskValues
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.gpu.GpuHealth
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceSpeed
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceValues
import com.krillsson.sysapi.core.history.series.MetricId
import com.krillsson.sysapi.core.history.series.MetricKind
import com.krillsson.sysapi.core.history.series.MetricSeriesBucketEntity
import java.time.Instant

class SeriesSnapshot(private val buckets: List<MetricSeriesBucketEntity>) {

    private val byMetric: Map<MetricId, Map<String, MetricSeriesBucketEntity>> = buckets
        .groupBy { it.metric }
        .mapValues { (_, rows) -> rows.associateBy { it.itemId } }

    fun value(metric: MetricId, itemId: String = MetricId.HOST_WIDE): Double? =
        byMetric[metric]?.get(itemId)?.collapse()

    fun flag(metric: MetricId, itemId: String = MetricId.HOST_WIDE): Boolean? =
        byMetric[metric]?.get(itemId)?.let { it.minValue == 1.0 }

    fun itemIds(vararg metrics: MetricId): List<String> = metrics
        .flatMap { byMetric[it]?.keys.orEmpty() }
        .distinct()
        .sorted()

    private fun MetricSeriesBucketEntity.collapse(): Double = when (metric.kind) {
        MetricKind.GAUGE -> avgValue
        MetricKind.COUNTER -> lastValue
        MetricKind.FLAG -> minValue
    }
}

object SeriesEntryBuilder {

    fun systemPoint(timestamp: Instant, snapshot: SeriesSnapshot) = SystemHistoryPoint.Synthesized(
        timestamp = timestamp,
        processorMetrics = snapshot.cpuLoad(),
        memoryMetrics = snapshot.memoryLoad(),
        connectivity = snapshot.connectivity(),
        networkInterfaceMetrics = snapshot.networkInterfaceLoads(),
        diskMetrics = snapshot.diskLoads(),
        fileSystemMetrics = snapshot.fileSystemLoads(),
        gpuMetrics = snapshot.gpuLoads()
    )

    private fun SeriesSnapshot.cpuLoad(): CpuLoad {
        val oneMinute = value(MetricId.LOAD_AVERAGE_1M) ?: -1.0
        return CpuLoad(
            usagePercentage = value(MetricId.CPU_USAGE_PERCENT) ?: 0.0,
            systemLoadAverage = oneMinute,
            loadAverages = LoadAverages(
                oneMinute = oneMinute,
                fiveMinutes = value(MetricId.LOAD_AVERAGE_5M) ?: -1.0,
                fifteenMinutes = value(MetricId.LOAD_AVERAGE_15M) ?: -1.0
            ),
            coreLoads = emptyList(),
            cpuHealth = CpuHealth(
                temperatures = listOfNotNull(value(MetricId.CPU_TEMPERATURE)),
                voltage = -1.0,
                fanRpm = value(MetricId.CPU_FAN_RPM) ?: -1.0,
                fanPercent = value(MetricId.CPU_FAN_PERCENT) ?: -1.0
            ),
            processCount = value(MetricId.CPU_PROCESS_COUNT)?.toInt() ?: 0,
            threadCount = value(MetricId.CPU_THREAD_COUNT)?.toInt() ?: 0
        )
    }

    private fun SeriesSnapshot.memoryLoad(): MemoryLoad {
        val total = value(MetricId.MEMORY_TOTAL_BYTES)?.toLong() ?: 0L
        val available = value(MetricId.MEMORY_AVAILABLE_BYTES)?.toLong() ?: 0L
        return MemoryLoad(
            numberOfProcesses = value(MetricId.CPU_PROCESS_COUNT)?.toInt() ?: 0,
            swapTotalBytes = value(MetricId.MEMORY_SWAP_TOTAL_BYTES)?.toLong() ?: 0L,
            swapUsedBytes = value(MetricId.MEMORY_SWAP_USED_BYTES)?.toLong() ?: 0L,
            totalBytes = total,
            availableBytes = available,
            usedPercent = value(MetricId.MEMORY_USED_PERCENT) ?: 0.0
        )
    }

    private fun SeriesSnapshot.connectivity() = Connectivity(
        externalIp = null,
        previousExternalIp = null,
        localIp = null,
        connected = flag(MetricId.CONNECTIVITY_UP) ?: false
    )

    private fun SeriesSnapshot.networkInterfaceLoads(): List<NetworkInterfaceLoad> = itemIds(
        MetricId.NETWORK_UP,
        MetricId.NETWORK_RX_BYTES_PER_SECOND,
        MetricId.NETWORK_TX_BYTES_PER_SECOND,
        MetricId.NETWORK_RX_BYTES,
        MetricId.NETWORK_TX_BYTES,
        MetricId.NETWORK_RX_PACKETS,
        MetricId.NETWORK_TX_PACKETS
    ).map { name ->
        NetworkInterfaceLoad(
            name = name,
            mac = "",
            isUp = flag(MetricId.NETWORK_UP, name) ?: false,
            values = NetworkInterfaceValues(
                speed = 0,
                bytesReceived = value(MetricId.NETWORK_RX_BYTES, name)?.toLong() ?: 0L,
                bytesSent = value(MetricId.NETWORK_TX_BYTES, name)?.toLong() ?: 0L,
                packetsReceived = value(MetricId.NETWORK_RX_PACKETS, name)?.toLong() ?: 0L,
                packetsSent = value(MetricId.NETWORK_TX_PACKETS, name)?.toLong() ?: 0L,
                inErrors = 0,
                outErrors = 0
            ),
            speed = NetworkInterfaceSpeed(
                receiveBytesPerSecond = value(MetricId.NETWORK_RX_BYTES_PER_SECOND, name)?.toLong() ?: 0L,
                sendBytesPerSecond = value(MetricId.NETWORK_TX_BYTES_PER_SECOND, name)?.toLong() ?: 0L
            )
        )
    }

    private fun SeriesSnapshot.diskLoads(): List<DiskLoad> = itemIds(
        MetricId.DISK_READ_BYTES_PER_SECOND,
        MetricId.DISK_WRITE_BYTES_PER_SECOND,
        MetricId.DISK_READS,
        MetricId.DISK_WRITES,
        MetricId.DISK_READ_BYTES,
        MetricId.DISK_WRITE_BYTES,
        MetricId.DISK_TEMPERATURE,
        MetricId.DISK_SMART_HEALTHY
    ).map { name ->
        DiskLoad(
            name = name,
            serial = name,
            values = DiskValues(
                reads = value(MetricId.DISK_READS, name)?.toLong() ?: 0L,
                readBytes = value(MetricId.DISK_READ_BYTES, name)?.toLong() ?: 0L,
                writes = value(MetricId.DISK_WRITES, name)?.toLong() ?: 0L,
                writeBytes = value(MetricId.DISK_WRITE_BYTES, name)?.toLong() ?: 0L
            ),
            speed = DiskSpeed(
                readBytesPerSecond = value(MetricId.DISK_READ_BYTES_PER_SECOND, name)?.toLong() ?: 0L,
                writeBytesPerSecond = value(MetricId.DISK_WRITE_BYTES_PER_SECOND, name)?.toLong() ?: 0L
            ),
            smartData = null,
            health = null
        )
    }

    private fun SeriesSnapshot.fileSystemLoads(): List<FileSystemLoad> = itemIds(
        MetricId.FILESYSTEM_FREE_BYTES,
        MetricId.FILESYSTEM_TOTAL_BYTES,
        MetricId.FILESYSTEM_USABLE_BYTES,
        MetricId.FILESYSTEM_USED_BYTES
    ).map { id ->
        FileSystemLoad(
            name = id,
            id = id,
            freeSpaceBytes = value(MetricId.FILESYSTEM_FREE_BYTES, id)?.toLong() ?: 0L,
            usableSpaceBytes = value(MetricId.FILESYSTEM_USABLE_BYTES, id)?.toLong() ?: 0L,
            totalSpaceBytes = value(MetricId.FILESYSTEM_TOTAL_BYTES, id)?.toLong() ?: 0L
        )
    }

    private fun SeriesSnapshot.gpuLoads(): List<GpuLoad> = itemIds(
        MetricId.GPU_CORE_PERCENT,
        MetricId.GPU_VRAM_USED_BYTES,
        MetricId.GPU_VRAM_TOTAL_BYTES,
        MetricId.GPU_TEMPERATURE,
        MetricId.GPU_FAN_PERCENT,
        MetricId.GPU_POWER_DRAW,
        MetricId.GPU_CORE_CLOCK_MHZ,
        MetricId.GPU_MEMORY_CLOCK_MHZ
    ).map { id ->
        GpuLoad(
            id = id,
            name = id,
            coreLoad = value(MetricId.GPU_CORE_PERCENT, id) ?: 0.0,
            vramUsedBytes = value(MetricId.GPU_VRAM_USED_BYTES, id)?.toLong() ?: 0L,
            vramTotalBytes = value(MetricId.GPU_VRAM_TOTAL_BYTES, id)?.toLong() ?: 0L,
            health = GpuHealth(
                temperature = value(MetricId.GPU_TEMPERATURE, id) ?: -1.0,
                fanPercent = value(MetricId.GPU_FAN_PERCENT, id) ?: -1.0,
                powerDraw = value(MetricId.GPU_POWER_DRAW, id) ?: -1.0,
                coreClockMhz = value(MetricId.GPU_CORE_CLOCK_MHZ, id)?.toLong() ?: -1L,
                memoryClockMhz = value(MetricId.GPU_MEMORY_CLOCK_MHZ, id)?.toLong() ?: -1L
            )
        )
    }
}
