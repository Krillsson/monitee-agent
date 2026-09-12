package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.config.HistoryConfiguration
import com.krillsson.sysapi.config.MetricSamplingConfiguration
import com.krillsson.sysapi.config.MetricSeriesConfiguration
import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.RetentionConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
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
import com.krillsson.sysapi.smart.DeviceHealth
import com.krillsson.sysapi.smart.HealthStatus
import com.krillsson.sysapi.smart.SmartData
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

fun at(timestamp: String): Instant = Instant.parse(timestamp)

fun seriesBucket(
    resolution: MetricResolution,
    bucketStart: Instant,
    samples: Int = 1,
    minValue: Double = 1.0,
    avgValue: Double = 2.0,
    maxValue: Double = 3.0,
    lastValue: Double = 2.5,
    metric: MetricId = MetricId.CPU_USAGE_PERCENT,
    itemId: String = MetricId.HOST_WIDE
) = MetricSeriesBucketEntity(
    id = UUID.randomUUID(),
    metric = metric,
    itemId = itemId,
    resolution = resolution,
    bucketStart = bucketStart,
    samples = samples,
    minValue = minValue,
    avgValue = avgValue,
    maxValue = maxValue,
    lastValue = lastValue
)

fun rawBucket(
    bucketStart: Instant,
    value: Double,
    metric: MetricId = MetricId.CPU_USAGE_PERCENT,
    itemId: String = MetricId.HOST_WIDE
) = seriesBucket(
    resolution = MetricResolution.RAW,
    bucketStart = bucketStart,
    samples = 1,
    minValue = value,
    avgValue = value,
    maxValue = value,
    lastValue = value,
    metric = metric,
    itemId = itemId
)

fun configWithSeriesRetention(
    raw: RetentionConfiguration = RetentionConfiguration(12, ChronoUnit.HOURS),
    fiveMinute: RetentionConfiguration = RetentionConfiguration(72, ChronoUnit.HOURS),
    hourly: RetentionConfiguration = RetentionConfiguration(90, ChronoUnit.DAYS),
    daily: RetentionConfiguration = RetentionConfiguration(730, ChronoUnit.DAYS),
    sampling: MetricSamplingConfiguration = MetricSamplingConfiguration()
): YAMLConfigFile {
    val config = mockk<YAMLConfigFile>()
    every { config.metricsConfig } returns MetricsConfiguration(
        history = HistoryConfiguration(
            series = MetricSeriesConfiguration(
                sampling = sampling,
                raw = raw,
                fiveMinute = fiveMinute,
                hourly = hourly,
                daily = daily
            )
        )
    )
    return config
}

fun cpuLoad(
    usagePercentage: Double = 42.0,
    temperatures: List<Double> = listOf(50.0, 60.0),
    fanPercent: Double = 0.0,
    fanRpm: Double = 0.0,
    processCount: Int = 100,
    threadCount: Int = 200
) = CpuLoad(
    usagePercentage = usagePercentage,
    systemLoadAverage = 1.0,
    loadAverages = LoadAverages(1.0, 2.0, 3.0),
    coreLoads = emptyList(),
    cpuHealth = CpuHealth(temperatures, 1.2, fanRpm, fanPercent),
    processCount = processCount,
    threadCount = threadCount
)

fun memoryLoad(
    totalBytes: Long = 16_000_000_000,
    availableBytes: Long = 4_000_000_000
) = MemoryLoad(
    numberOfProcesses = 100,
    swapTotalBytes = 2_000_000_000,
    swapUsedBytes = 500_000_000,
    totalBytes = totalBytes,
    availableBytes = availableBytes,
    usedPercent = 75.0
)

fun networkInterfaceLoad(
    name: String = "eth0",
    isUp: Boolean = true,
    receiveBytesPerSecond: Long = 1000,
    sendBytesPerSecond: Long = 2000
) = NetworkInterfaceLoad(
    name = name,
    mac = "00:11:22:33:44:55",
    isUp = isUp,
    values = NetworkInterfaceValues(1_000_000_000, 500, 600, 700, 800, 0, 0),
    speed = NetworkInterfaceSpeed(receiveBytesPerSecond, sendBytesPerSecond)
)

fun gpuLoad(
    id: String = "gpu-0",
    coreLoad: Double = 33.0,
    temperature: Double = 65.0,
    fanPercent: Double = 0.0,
    powerDraw: Double = 0.0,
    coreClockMhz: Long = 0,
    memoryClockMhz: Long = 0
) = GpuLoad(
    id = id,
    name = "Test GPU",
    coreLoad = coreLoad,
    vramUsedBytes = 2_000_000_000,
    vramTotalBytes = 8_000_000_000,
    health = GpuHealth(temperature, fanPercent, powerDraw, coreClockMhz, memoryClockMhz)
)

fun diskLoad(
    name: String = "sda",
    readBytesPerSecond: Long = 1024,
    writeBytesPerSecond: Long = 2048,
    smartTemperature: Int? = null,
    healthStatus: HealthStatus? = null
) = DiskLoad(
    name = name,
    serial = "SERIAL-$name",
    values = DiskValues(10, 1000, 20, 2000),
    speed = DiskSpeed(readBytesPerSecond, writeBytesPerSecond),
    smartData = smartTemperature?.let { temperature ->
        SmartData.Hdd(
            name = name,
            temperatureCelsius = temperature,
            powerOnHours = null,
            powerCycleCount = null,
            rawAttributes = emptyMap(),
            reallocatedSectors = null,
            pendingSectors = null,
            uncorrectableSectors = null,
            offlineUncorrectable = null,
            spinRetryCount = null,
            seekErrorRate = null,
            udmaCrcErrors = null
        )
    },
    health = healthStatus?.let { DeviceHealth(it, emptyList()) }
)

fun fileSystemLoad(
    id: String = "fs-1",
    freeSpaceBytes: Long = 100,
    totalSpaceBytes: Long = 500
) = FileSystemLoad(
    name = "/",
    id = id,
    freeSpaceBytes = freeSpaceBytes,
    usableSpaceBytes = freeSpaceBytes,
    totalSpaceBytes = totalSpaceBytes
)

fun connectivity(
    connected: Boolean = true,
    externalIp: String? = "1.2.3.4",
    previousExternalIp: String? = "1.2.3.4"
) = Connectivity(
    externalIp = externalIp,
    previousExternalIp = previousExternalIp,
    localIp = "192.168.1.2",
    connected = connected
)
