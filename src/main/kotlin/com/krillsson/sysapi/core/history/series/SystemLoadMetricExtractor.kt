package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.history.series.MetricId.Companion.HOST_WIDE
import com.krillsson.sysapi.smart.HealthStatus
import java.time.Instant

object SystemLoadMetricExtractor {

    fun cpu(load: CpuLoad, timestamp: Instant): List<MetricSample> = buildList {
        add(sample(MetricId.CPU_USAGE_PERCENT, HOST_WIDE, timestamp, load.usagePercentage))
        add(sample(MetricId.CPU_PROCESS_COUNT, HOST_WIDE, timestamp, load.processCount.toDouble()))
        add(sample(MetricId.CPU_THREAD_COUNT, HOST_WIDE, timestamp, load.threadCount.toDouble()))
        add(sample(MetricId.LOAD_AVERAGE_1M, HOST_WIDE, timestamp, load.loadAverages.oneMinute))
        add(sample(MetricId.LOAD_AVERAGE_5M, HOST_WIDE, timestamp, load.loadAverages.fiveMinutes))
        add(sample(MetricId.LOAD_AVERAGE_15M, HOST_WIDE, timestamp, load.loadAverages.fifteenMinutes))
        load.cpuHealth.temperatures.filter { it > 0.0 }.takeIf { it.isNotEmpty() }?.let { temperatures ->
            add(sample(MetricId.CPU_TEMPERATURE, HOST_WIDE, timestamp, temperatures.average()))
        }
        load.cpuHealth.fanPercent.takeIf { it > 0.0 }?.let {
            add(sample(MetricId.CPU_FAN_PERCENT, HOST_WIDE, timestamp, it))
        }
        load.cpuHealth.fanRpm.takeIf { it > 0.0 }?.let {
            add(sample(MetricId.CPU_FAN_RPM, HOST_WIDE, timestamp, it))
        }
    }

    fun memory(load: MemoryLoad, timestamp: Instant): List<MetricSample> = listOf(
        sample(MetricId.MEMORY_USED_BYTES, HOST_WIDE, timestamp, load.usedBytes.toDouble()),
        sample(MetricId.MEMORY_AVAILABLE_BYTES, HOST_WIDE, timestamp, load.availableBytes.toDouble()),
        sample(MetricId.MEMORY_TOTAL_BYTES, HOST_WIDE, timestamp, load.totalBytes.toDouble()),
        sample(MetricId.MEMORY_USED_PERCENT, HOST_WIDE, timestamp, load.usedPercent),
        sample(MetricId.MEMORY_SWAP_USED_BYTES, HOST_WIDE, timestamp, load.swapUsedBytes.toDouble()),
        sample(MetricId.MEMORY_SWAP_TOTAL_BYTES, HOST_WIDE, timestamp, load.swapTotalBytes.toDouble())
    )

    fun connectivity(connectivity: Connectivity, timestamp: Instant): List<MetricSample> = buildList {
        add(sample(MetricId.CONNECTIVITY_UP, HOST_WIDE, timestamp, connectivity.connected.asFlag()))
        if (connectivity.externalIp != null) {
            val stable = connectivity.previousExternalIp == null ||
                connectivity.externalIp == connectivity.previousExternalIp
            add(sample(MetricId.CONNECTIVITY_EXTERNAL_IP_STABLE, HOST_WIDE, timestamp, stable.asFlag()))
        }
    }

    fun networkInterfaces(loads: List<NetworkInterfaceLoad>, timestamp: Instant): List<MetricSample> =
        loads.flatMap { load ->
            listOf(
                sample(MetricId.NETWORK_UP, load.name, timestamp, load.isUp.asFlag()),
                sample(
                    MetricId.NETWORK_RX_BYTES_PER_SECOND,
                    load.name,
                    timestamp,
                    load.speed.receiveBytesPerSecond.toDouble()
                ),
                sample(
                    MetricId.NETWORK_TX_BYTES_PER_SECOND,
                    load.name,
                    timestamp,
                    load.speed.sendBytesPerSecond.toDouble()
                ),
                sample(MetricId.NETWORK_RX_BYTES, load.name, timestamp, load.values.bytesReceived.toDouble()),
                sample(MetricId.NETWORK_TX_BYTES, load.name, timestamp, load.values.bytesSent.toDouble()),
                sample(MetricId.NETWORK_RX_PACKETS, load.name, timestamp, load.values.packetsReceived.toDouble()),
                sample(MetricId.NETWORK_TX_PACKETS, load.name, timestamp, load.values.packetsSent.toDouble())
            )
        }

    fun gpus(loads: List<GpuLoad>, timestamp: Instant): List<MetricSample> = loads.flatMap { load ->
        buildList {
            add(sample(MetricId.GPU_CORE_PERCENT, load.id, timestamp, load.coreLoad))
            add(sample(MetricId.GPU_VRAM_USED_BYTES, load.id, timestamp, load.vramUsedBytes.toDouble()))
            add(sample(MetricId.GPU_VRAM_TOTAL_BYTES, load.id, timestamp, load.vramTotalBytes.toDouble()))
            load.health.temperature.takeIf { it > 0.0 }?.let {
                add(sample(MetricId.GPU_TEMPERATURE, load.id, timestamp, it))
            }
            load.health.fanPercent.takeIf { it > 0.0 }?.let {
                add(sample(MetricId.GPU_FAN_PERCENT, load.id, timestamp, it))
            }
            load.health.powerDraw.takeIf { it > 0.0 }?.let {
                add(sample(MetricId.GPU_POWER_DRAW, load.id, timestamp, it))
            }
            load.health.coreClockMhz.takeIf { it > 0L }?.let {
                add(sample(MetricId.GPU_CORE_CLOCK_MHZ, load.id, timestamp, it.toDouble()))
            }
            load.health.memoryClockMhz.takeIf { it > 0L }?.let {
                add(sample(MetricId.GPU_MEMORY_CLOCK_MHZ, load.id, timestamp, it.toDouble()))
            }
        }
    }

    fun diskRates(loads: List<DiskLoad>, timestamp: Instant): List<MetricSample> = loads.flatMap { load ->
        buildList {
            if (load.speed.readBytesPerSecond >= 0) {
                add(
                    sample(
                        MetricId.DISK_READ_BYTES_PER_SECOND,
                        load.name,
                        timestamp,
                        load.speed.readBytesPerSecond.toDouble()
                    )
                )
            }
            if (load.speed.writeBytesPerSecond >= 0) {
                add(
                    sample(
                        MetricId.DISK_WRITE_BYTES_PER_SECOND,
                        load.name,
                        timestamp,
                        load.speed.writeBytesPerSecond.toDouble()
                    )
                )
            }
            add(sample(MetricId.DISK_READS, load.name, timestamp, load.values.reads.toDouble()))
            add(sample(MetricId.DISK_WRITES, load.name, timestamp, load.values.writes.toDouble()))
            add(sample(MetricId.DISK_READ_BYTES, load.name, timestamp, load.values.readBytes.toDouble()))
            add(sample(MetricId.DISK_WRITE_BYTES, load.name, timestamp, load.values.writeBytes.toDouble()))
        }
    }

    fun diskHealth(loads: List<DiskLoad>, timestamp: Instant): List<MetricSample> = loads.flatMap { load ->
        buildList {
            load.temperature?.let {
                add(sample(MetricId.DISK_TEMPERATURE, load.name, timestamp, it.toDouble()))
            }
            load.health?.let { health ->
                add(
                    sample(
                        MetricId.DISK_SMART_HEALTHY,
                        load.name,
                        timestamp,
                        (health.status == HealthStatus.HEALTHY).asFlag()
                    )
                )
            }
        }
    }

    fun fileSystems(loads: List<FileSystemLoad>, timestamp: Instant): List<MetricSample> = loads.flatMap { load ->
        listOf(
            sample(MetricId.FILESYSTEM_FREE_BYTES, load.id, timestamp, load.freeSpaceBytes.toDouble()),
            sample(MetricId.FILESYSTEM_USABLE_BYTES, load.id, timestamp, load.usableSpaceBytes.toDouble()),
            sample(MetricId.FILESYSTEM_TOTAL_BYTES, load.id, timestamp, load.totalSpaceBytes.toDouble()),
            sample(
                MetricId.FILESYSTEM_USED_BYTES,
                load.id,
                timestamp,
                (load.totalSpaceBytes - load.freeSpaceBytes).coerceAtLeast(0).toDouble()
            )
        )
    }
}

internal fun sample(metric: MetricId, itemId: String, timestamp: Instant, value: Double) =
    MetricSample(metric, itemId, timestamp, value)

internal fun Boolean.asFlag(): Double = if (this) 1.0 else 0.0
