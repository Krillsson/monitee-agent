package com.krillsson.sysapi.core.metrics.windows

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultDiskSensors
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore


@Lazy
@Component
open class WindowsDiskSensors(private val monitorManager: OHMManager) : DefaultDiskSensors() {
    override fun getDiskTemperature(hwDiskStore: HWDiskStore): Double? {
        monitorManager.update()
        val partitionNames = hwDiskStore.partitions.map { it.mountPoint }
        return monitorManager.driveMonitors()
            .firstOrNull { partitionNames.contains(it.logicalName) }
            ?.temperature
            ?.value
    }
}
