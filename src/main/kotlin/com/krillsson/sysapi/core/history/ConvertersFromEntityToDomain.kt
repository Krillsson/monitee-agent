package com.krillsson.sysapi.core.history

import com.krillsson.sysapi.core.domain.cpu.CoreLoad
import com.krillsson.sysapi.core.domain.cpu.CpuHealth
import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.domain.history.SystemHistoryEntry
import com.krillsson.sysapi.core.domain.sensors.HealthData
import com.krillsson.sysapi.core.history.db.*
import com.krillsson.sysapi.smart.HealthStatus
import com.krillsson.sysapi.smart.SmartData

fun HistorySystemLoadEntity.asSystemHistoryEntry(): SystemHistoryEntry {
    return SystemHistoryEntry(
            id,
            date,
            HistorySystemLoad(
                    uptime,
                    systemLoadAverage,
                    cpuLoad.asCpuLoad(),
                    networkInterfaceLoads.map { it.asNetworkInterfaceLoad() },
                    connectivity.asConnectivity(),
                    diskLoads?.map { it.asDiskLoad() }.orEmpty(),
                    fileSystemLoads?.map { it.asFileSystemLoad() }.orEmpty(),
                    memory.asMemoryLoad(),
                    gpuLoads.map { it.asGpuLoad() },
                    motherboardHealth.map { it.asHealthData() }
            )
    )
}

fun FileSystemLoad.asFileSystemLoad(): com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad {
    return com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad(
            name,
            fsId.orEmpty(),
            freeSpaceBytes,
            usableSpaceBytes,
            totalSpaceBytes
    )
}

fun DiskLoad.asDiskLoad(): com.krillsson.sysapi.core.domain.disk.DiskLoad {
    return com.krillsson.sysapi.core.domain.disk.DiskLoad(
        name,
        serial,
        values.asValues(),
        speed.asSpeed(),
        smartData = smart?.asDomain(),
        health = health?.asDomain()
    )
}

private fun DiskSpeed.asSpeed(): com.krillsson.sysapi.core.domain.disk.DiskSpeed {
    return com.krillsson.sysapi.core.domain.disk.DiskSpeed(readBytesPerSecond, writeBytesPerSecond)
}

private fun DiskValues.asValues(): com.krillsson.sysapi.core.domain.disk.DiskValues {
    return com.krillsson.sysapi.core.domain.disk.DiskValues(reads, readBytes, writes, writeBytes)
}

fun com.krillsson.sysapi.core.history.db.HealthData.asHealthData(): HealthData {
    return HealthData(
            description, data, dataType.asDataType()
    )
}

fun GpuLoad.asGpuLoad(): com.krillsson.sysapi.core.domain.gpu.GpuLoad {
    return com.krillsson.sysapi.core.domain.gpu.GpuLoad(
            name, coreLoad, memoryLoad, health.asHealth()
    )
}

fun GpuHealth.asHealth(): com.krillsson.sysapi.core.domain.gpu.GpuHealth {
    return com.krillsson.sysapi.core.domain.gpu.GpuHealth(
            fanRpm, fanPercent, temperature
    )
}

fun MemoryLoad.asMemoryLoad(): com.krillsson.sysapi.core.domain.memory.MemoryLoad {
    return com.krillsson.sysapi.core.domain.memory.MemoryLoad(
            numberOfProcesses,
            swapTotalBytes,
            swapUsedBytes,
            totalBytes,
            availableBytes,
            usedPercent
    )
}

fun DataType.asDataType(): com.krillsson.sysapi.core.domain.sensors.DataType {
    return when (this) {
        DataType.CLOCK -> com.krillsson.sysapi.core.domain.sensors.DataType.CLOCK
        DataType.VOLTAGE -> com.krillsson.sysapi.core.domain.sensors.DataType.VOLTAGE
        DataType.PERCENT -> com.krillsson.sysapi.core.domain.sensors.DataType.PERCENT
        DataType.RPM -> com.krillsson.sysapi.core.domain.sensors.DataType.RPM
        DataType.CELCIUS -> com.krillsson.sysapi.core.domain.sensors.DataType.CELCIUS
        DataType.GIGABYTE -> com.krillsson.sysapi.core.domain.sensors.DataType.GIGABYTE
    }
}

fun Connectivity.asConnectivity(): com.krillsson.sysapi.core.domain.network.Connectivity {
    return com.krillsson.sysapi.core.domain.network.Connectivity(
            externalIp, previousExternalIp, localIp, connected
    )
}

fun NetworkInterfaceLoad.asNetworkInterfaceLoad(): com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad {
    return com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad(
            name,
            mac,
            isUp,
            values.asNetworkInterfaceValues(),
            speed.asNetworkInterfaceSpeed()
    )
}

fun NetworkInterfaceSpeed.asNetworkInterfaceSpeed(): com.krillsson.sysapi.core.domain.network.NetworkInterfaceSpeed {
    return com.krillsson.sysapi.core.domain.network.NetworkInterfaceSpeed(receiveBytesPerSecond, sendBytesPerSecond)
}

fun NetworkInterfaceValues.asNetworkInterfaceValues(): com.krillsson.sysapi.core.domain.network.NetworkInterfaceValues {
    return com.krillsson.sysapi.core.domain.network.NetworkInterfaceValues(
            speed, bytesReceived, bytesSent, packetsReceived, packetsSent, inErrors, outErrors
    )
}

fun com.krillsson.sysapi.core.history.db.CpuLoad.asCpuLoad(): CpuLoad {
    return CpuLoad(
            usagePercentage,
            systemLoadAverage,
            loadAverages.asLoadAverages(),
            coreLoads.map { it.asCoreLoad() },
            cpuHealth.asCpuHealth(),
            processCount,
            threadCount
    )
}

private fun LoadAverages.asLoadAverages(): com.krillsson.sysapi.core.domain.cpu.LoadAverages {
    return com.krillsson.sysapi.core.domain.cpu.LoadAverages(oneMinutes, fiveMinutes, fifteenMinutes)
}

fun com.krillsson.sysapi.core.history.db.CpuHealth.asCpuHealth(): CpuHealth {
    return CpuHealth(
            temperatures, voltage, fanRpm, fanPercent
    )
}

fun com.krillsson.sysapi.core.history.db.CoreLoad.asCoreLoad(): CoreLoad {
    return CoreLoad(
            percentage
    )
}

fun ContainerStatisticsEntity.asContainerStatisticsHistoryEntry(): com.krillsson.sysapi.core.domain.docker.ContainerMetricsHistoryEntry {
    return com.krillsson.sysapi.core.domain.docker.ContainerMetricsHistoryEntry(
            containerId,
            timestamp,
            asContainerStatistics()
    )
}

fun ContainerStatisticsEntity.asContainerStatistics(): ContainerMetrics {
    return ContainerMetrics(
            id = containerId,
            cpuUsage = cpuUsage.asCpuUsage(),
            memoryUsage = memoryUsage.asMemoryUsage(),
            currentPid = currentPid,
            networkUsage = networkUsage.asNetworkUsage(),
            blockIOUsage = blockIOUsage.asBlockIOUsage()
    )
}

private fun BlockIOUsage.asBlockIOUsage(): com.krillsson.sysapi.core.domain.docker.BlockIOUsage {
    return com.krillsson.sysapi.core.domain.docker.BlockIOUsage(
            bytesWritten = bytesWritten,
            bytesRead = bytesRead
    )
}

private fun NetworkUsage.asNetworkUsage(): com.krillsson.sysapi.core.domain.docker.NetworkUsage {
    return com.krillsson.sysapi.core.domain.docker.NetworkUsage(
            bytesReceived = bytesReceived,
            bytesTransferred = bytesTransferred
    )
}

private fun MemoryUsage.asMemoryUsage(): com.krillsson.sysapi.core.domain.docker.MemoryUsage {
    return com.krillsson.sysapi.core.domain.docker.MemoryUsage(
            usageBytes = usageBytes,
            usagePercent = usagePercent,
            limitBytes = limitBytes
    )
}

private fun CpuUsage.asCpuUsage(): com.krillsson.sysapi.core.domain.docker.CpuUsage {
    return com.krillsson.sysapi.core.domain.docker.CpuUsage(
            usagePercentPerCore = usagePercentPerCore,
            usagePercentTotal = usagePercentTotal,
            throttlingData = throttlingData.asThrottlingData()
    )
}

private fun ThrottlingData.asThrottlingData(): com.krillsson.sysapi.core.domain.docker.ThrottlingData {
    return com.krillsson.sysapi.core.domain.docker.ThrottlingData(
            periods = periods,
            throttledPeriods = throttledPeriods,
            throttledTime = throttledTime
    )
}

// SmartData & DeviceHealth converters

private fun SmartDataEmbedded.asDomain(): SmartData? {
    return when (deviceType) {
        SmartDataEmbedded.SmartType.HDD -> SmartData.Hdd(
            name = "HDD",
            temperatureCelsius = temperatureCelsius,
            powerOnHours = powerOnHours,
            powerCycleCount = powerCycleCount,
            rawAttributes = emptyMap(),
            reallocatedSectors = hddReallocatedSectors,
            pendingSectors = hddPendingSectors,
            uncorrectableSectors = hddUncorrectableSectors,
            offlineUncorrectable = hddOfflineUncorrectable,
            spinRetryCount = hddSpinRetryCount,
            seekErrorRate = hddSeekErrorRate,
            udmaCrcErrors = hddUdmaCrcErrors
        )
        SmartDataEmbedded.SmartType.SATA_SSD -> SmartData.SataSsd(
            name = "SATA SSD",
            temperatureCelsius = temperatureCelsius,
            powerOnHours = powerOnHours,
            powerCycleCount = powerCycleCount,
            rawAttributes = emptyMap(),
            percentageUsed = ssdPercentageUsed,
            wearLevelingCount = ssdWearLevelingCount,
            availableReservedSpace = ssdAvailableReservedSpace,
            totalWriteGiB = ssdTotalWriteGiB,
            totalReadGiB = ssdTotalReadGiB,
            mediaErrors = ssdMediaErrors,
            uncorrectableErrors = ssdUncorrectableErrors,
            udmaCrcErrors = ssdUdmaCrcErrors
        )
        SmartDataEmbedded.SmartType.NVME -> SmartData.Nvme(
            name = "NVMe",
            temperatureCelsius = temperatureCelsius,
            powerOnHours = powerOnHours,
            powerCycleCount = powerCycleCount,
            rawAttributes = emptyMap(),
            percentageUsed = nvmePercentageUsed,
            dataUnitsRead = nvmeDataUnitsRead,
            dataUnitsWritten = nvmeDataUnitsWritten,
            mediaErrors = nvmeMediaErrors,
            numErrLogEntries = nvmeNumErrLogEntries,
            unsafeShutdowns = nvmeUnsafeShutdowns,
            controllerBusyTimeMinutes = nvmeControllerBusyTimeMinutes,
            vendorData = emptyMap()
        )
        null -> null
    }
}

private fun DeviceHealth.asDomain(): com.krillsson.sysapi.smart.DeviceHealth {
    return com.krillsson.sysapi.smart.DeviceHealth(
        status = healthStatus?.let { HealthStatus.valueOf(it) }?: HealthStatus.HEALTHY,
        messages = healthMessages.orEmpty()
    )
}
