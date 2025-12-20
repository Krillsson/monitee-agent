package com.krillsson.sysapi.core.metrics.windows

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultDiskSensors
import com.krillsson.sysapi.smart.SmartData
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore


@Lazy
@Component
open class WindowsDiskSensors(private val monitorManager: OHMManager) : DefaultDiskSensors() {
    override fun getSmartData(hwDiskStore: HWDiskStore): SmartData? {
        monitorManager.update()
        val partitionNames = hwDiskStore.partitions.map { it.mountPoint }
        return SmartData.Hdd(
            name = hwDiskStore.name,
            temperatureCelsius = monitorManager.driveMonitors()
                .firstOrNull { partitionNames.contains(it.logicalName) }
                ?.temperature
                ?.value?.toInt(),
            powerOnHours = 0L,
            powerCycleCount = 0L,
            rawAttributes = emptyMap(),
            reallocatedSectors = 0L,
            pendingSectors = 0L,
            uncorrectableSectors = 0L,
            offlineUncorrectable = 0L,
            spinRetryCount = 0L,
            seekErrorRate = 0L,
            udmaCrcErrors = 0L
        )
    }
}
