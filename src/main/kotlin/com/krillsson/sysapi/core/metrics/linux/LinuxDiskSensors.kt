package com.krillsson.sysapi.core.metrics.linux

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultDiskSensors
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore

@Lazy
@Component
open class LinuxDiskSensors(private val smartCtl: SmartCtl) : DefaultDiskSensors() {
    private val supportsSmartCtl = smartCtl.supportsCommand()
    override fun getDiskTemperature(hwDiskStore: HWDiskStore): Double? {
        return if (supportsSmartCtl) {
            smartCtl.getSmartData(hwDiskStore.name)?.temperature?.current?.toDouble()
        } else {
            null
        }
    }
}