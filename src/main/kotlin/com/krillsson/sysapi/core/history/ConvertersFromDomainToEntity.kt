package com.krillsson.sysapi.core.history

import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.domain.history.SystemHistoryEntry
import com.krillsson.sysapi.core.history.db.*
import com.krillsson.sysapi.smart.SmartData
import java.time.Instant
import java.util.*

fun SystemHistoryEntry.asEntity(): HistorySystemLoadEntity {
    return value.asEntity(id, date)
}

fun HistorySystemLoad.asEntity(id: UUID, dateTime: Instant): HistorySystemLoadEntity {
    return HistorySystemLoadEntity(
        id,
        dateTime,
        uptime,
        systemLoadAverage,
        cpuLoad.asCpuLoadEntity(id),
        networkInterfaceLoads.map { it.asNetworkInterfaceLoad(id) },
        connectivity.asConnectivity(id),
        diskLoads.map { it.asDiskLoad(id) },
        fileSystemLoads.map { it.asFileSystemLoad(id) },
        memory.asMemoryLoad(id),
        gpuLoads.map { it.asGpuLoad(id) },
        motherboardHealth.map { it.asMotherboardHealthData(id) }
    )
}

private fun com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad.asFileSystemLoad(uuid: UUID): FileSystemLoad {
    return FileSystemLoad(
        UUID.randomUUID(),
        null,
        uuid,
        name,
        id,
        freeSpaceBytes,
        usableSpaceBytes,
        totalSpaceBytes
    )
}

private fun com.krillsson.sysapi.core.domain.disk.DiskLoad.asDiskLoad(id: UUID): DiskLoad {
    return DiskLoad(
        UUID.randomUUID(),
        null,
        id,
        name,
        serial,
        smartData?.temperatureCelsius?.toDouble(),
        values.asDiskValues(),
        speed.asSpeed(),
        smart = smartData?.asEmbedded(),
        health = health?.asEmbedded()
    )
}

private fun com.krillsson.sysapi.core.domain.disk.DiskSpeed.asSpeed(): DiskSpeed {
    return DiskSpeed(
        readBytesPerSecond, writeBytesPerSecond
    )
}

private fun com.krillsson.sysapi.core.domain.disk.DiskValues.asDiskValues(): DiskValues {
    return DiskValues(
        reads,
        readBytes,
        writes,
        writeBytes
    )
}

private fun com.krillsson.sysapi.core.domain.sensors.HealthData.asMotherboardHealthData(id: UUID): HealthData {
    return HealthData(
        UUID.randomUUID(),
        null,
        id,
        description,
        data,
        dataType.asDataType()
    )
}

private fun com.krillsson.sysapi.core.domain.gpu.GpuLoad.asGpuLoad(id: UUID): GpuLoad {
    return GpuLoad(
        UUID.randomUUID(),
        null,
        id,
        name,
        coreLoad,
        memoryLoad,
        health.asGpuHealth()
    )
}

private fun com.krillsson.sysapi.core.domain.gpu.GpuHealth.asGpuHealth(): GpuHealth {
    return GpuHealth(
        fanRpm, fanPercent, temperature
    )
}

private fun com.krillsson.sysapi.core.domain.memory.MemoryLoad.asMemoryLoad(id: UUID): MemoryLoad {
    return MemoryLoad(
        id,
        numberOfProcesses,
        swapTotalBytes,
        swapUsedBytes,
        totalBytes,
        availableBytes,
        usedPercent
    )
}

private fun com.krillsson.sysapi.core.domain.sensors.DataType.asDataType(): DataType {
    return when (this) {
        com.krillsson.sysapi.core.domain.sensors.DataType.CLOCK -> DataType.CLOCK
        com.krillsson.sysapi.core.domain.sensors.DataType.VOLTAGE -> DataType.VOLTAGE
        com.krillsson.sysapi.core.domain.sensors.DataType.PERCENT -> DataType.PERCENT
        com.krillsson.sysapi.core.domain.sensors.DataType.RPM -> DataType.RPM
        com.krillsson.sysapi.core.domain.sensors.DataType.CELCIUS -> DataType.CELCIUS
        com.krillsson.sysapi.core.domain.sensors.DataType.GIGABYTE -> DataType.GIGABYTE
    }
}

private fun com.krillsson.sysapi.core.domain.network.Connectivity.asConnectivity(id: UUID): Connectivity {
    return Connectivity(
        id,
        externalIp,
        previousExternalIp,
        localIp,
        connected
    )
}

private fun com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad.asNetworkInterfaceLoad(id: UUID): NetworkInterfaceLoad {
    return NetworkInterfaceLoad(
        UUID.randomUUID(),
        null,
        id,
        name,
        mac,
        isUp,
        values.asNetworkInterfaceValues(),
        speed.asSpeed(),
    )
}

private fun com.krillsson.sysapi.core.domain.network.NetworkInterfaceSpeed.asSpeed(): NetworkInterfaceSpeed {
    return NetworkInterfaceSpeed(
        receiveBytesPerSecond, sendBytesPerSecond
    )
}

private fun com.krillsson.sysapi.core.domain.network.NetworkInterfaceValues.asNetworkInterfaceValues(): NetworkInterfaceValues {
    return NetworkInterfaceValues(
        speed, bytesReceived, bytesSent, packetsReceived, packetsSent, inErrors, outErrors
    )
}

private fun com.krillsson.sysapi.core.domain.cpu.CpuLoad.asCpuLoadEntity(id: UUID): CpuLoad {
    return CpuLoad(
        id,
        usagePercentage,
        systemLoadAverage,
        loadAverages.asLoadAverage(id),
        coreLoads.map { it.asCoreLoad(id) },
        cpuHealth.asCpuHealth(id),
        processCount,
        threadCount
    )
}

private fun com.krillsson.sysapi.core.domain.cpu.LoadAverages.asLoadAverage(id: UUID): LoadAverages {
    return LoadAverages(
        id,
        oneMinute,
        fiveMinutes,
        fifteenMinutes
    )
}

private fun com.krillsson.sysapi.core.domain.cpu.CpuHealth.asCpuHealth(id: UUID): CpuHealth {
    return CpuHealth(
        id,
        temperatures,
        voltage,
        fanRpm,
        fanPercent
    )
}

private fun com.krillsson.sysapi.core.domain.cpu.CoreLoad.asCoreLoad(id: UUID): CoreLoad {
    return CoreLoad(
        UUID.randomUUID(),
        null,
        id,
        percentage
    )
}

fun ContainerMetrics.asEntity(): ContainerStatisticsEntity {
    return ContainerStatisticsEntity(
        UUID.randomUUID(),
        id,
        Instant.now(),
        currentPid,
        cpuUsage.asCpuUsageEntity(),
        memoryUsage.asMemoryUsageEntity(),
        networkUsage.asNetworkUsageEntity(),
        blockIOUsage.asBlockIOUsageEntity()
    )
}

private fun com.krillsson.sysapi.core.domain.docker.BlockIOUsage.asBlockIOUsageEntity(): BlockIOUsage {
    return BlockIOUsage(
        bytesWritten = bytesWritten,
        bytesRead = bytesRead
    )
}

private fun com.krillsson.sysapi.core.domain.docker.NetworkUsage.asNetworkUsageEntity(): NetworkUsage {
    return NetworkUsage(
        bytesReceived = bytesReceived,
        bytesTransferred = bytesTransferred
    )
}

private fun com.krillsson.sysapi.core.domain.docker.MemoryUsage.asMemoryUsageEntity(): MemoryUsage {
    return MemoryUsage(
        usagePercent = usagePercent,
        usageBytes = usageBytes,
        limitBytes = limitBytes
    )
}

private fun com.krillsson.sysapi.core.domain.docker.CpuUsage.asCpuUsageEntity(): CpuUsage {
    return CpuUsage(
        usagePercentPerCore = usagePercentPerCore,
        usagePercentTotal = usagePercentTotal,
        throttlingData = throttlingData.asThrottlingDataEntity()
    )
}

private fun com.krillsson.sysapi.core.domain.docker.ThrottlingData.asThrottlingDataEntity(): ThrottlingData {
    return ThrottlingData(periods, throttledPeriods, throttledTime)
}

// SmartData & DeviceHealth converters

private fun SmartData.asEmbedded(): SmartDataEmbedded {
    return when (this) {
        is SmartData.Hdd -> SmartDataEmbedded(
            deviceType = SmartDataEmbedded.SmartType.HDD,
            temperatureCelsius = temperatureCelsius,
            powerOnHours = powerOnHours,
            powerCycleCount = powerCycleCount,
            hddReallocatedSectors = reallocatedSectors,
            hddPendingSectors = pendingSectors,
            hddUncorrectableSectors = uncorrectableSectors,
            hddOfflineUncorrectable = offlineUncorrectable,
            hddSpinRetryCount = spinRetryCount,
            hddSeekErrorRate = seekErrorRate,
            hddUdmaCrcErrors = udmaCrcErrors
        )
        is SmartData.SataSsd -> SmartDataEmbedded(
            deviceType = SmartDataEmbedded.SmartType.SATA_SSD,
            temperatureCelsius = temperatureCelsius,
            powerOnHours = powerOnHours,
            powerCycleCount = powerCycleCount,
            ssdPercentageUsed = percentageUsed,
            ssdWearLevelingCount = wearLevelingCount,
            ssdAvailableReservedSpace = availableReservedSpace,
            ssdTotalWriteGiB = totalWriteGiB,
            ssdTotalReadGiB = totalReadGiB,
            ssdMediaErrors = mediaErrors,
            ssdUncorrectableErrors = uncorrectableErrors,
            ssdUdmaCrcErrors = udmaCrcErrors
        )
        is SmartData.Nvme -> SmartDataEmbedded(
            deviceType = SmartDataEmbedded.SmartType.NVME,
            temperatureCelsius = temperatureCelsius,
            powerOnHours = powerOnHours,
            powerCycleCount = powerCycleCount,
            nvmePercentageUsed = percentageUsed,
            nvmeDataUnitsRead = dataUnitsRead,
            nvmeDataUnitsWritten = dataUnitsWritten,
            nvmeMediaErrors = mediaErrors,
            nvmeNumErrLogEntries = numErrLogEntries,
            nvmeUnsafeShutdowns = unsafeShutdowns,
            nvmeControllerBusyTimeMinutes = controllerBusyTimeMinutes
        )
    }
}

private fun com.krillsson.sysapi.smart.DeviceHealth.asEmbedded(): DeviceHealth {
    return DeviceHealth(
        healthStatus = status.name,
        healthMessages = messages
    )
}
