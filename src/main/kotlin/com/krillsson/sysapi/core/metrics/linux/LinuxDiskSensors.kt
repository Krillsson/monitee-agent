package com.krillsson.sysapi.core.metrics.linux

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultDiskSensors
import com.krillsson.sysapi.smart.DeviceHealth
import com.krillsson.sysapi.smart.SmartData
import com.krillsson.sysapi.smart.SmartManager
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore

@Lazy
@Component
open class LinuxDiskSensors(
    private val smartManager: SmartManager
) : DefaultDiskSensors() {
    private val supportsSmartCtl = smartManager.supportsSmartCommand()
    override fun getSmartData(hwDiskStore: HWDiskStore): SmartData? {
        return if (supportsSmartCtl) {
            smartManager.getSmartData(hwDiskStore.name)
        } else {
            null
        }
    }

    override fun getDiskHealth(smartData: SmartData): DeviceHealth? {
        return if (supportsSmartCtl) {
            smartManager.health(smartData)
        } else {
            null
        }
    }
}