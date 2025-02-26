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
        // TODO needs to be tested before merge
        return monitorManager.driveMonitors()
            .firstOrNull { it.name == hwDiskStore.name }
            ?.temperature
            ?.value
    }
}