package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.smart.DeviceHealth
import com.krillsson.sysapi.smart.SmartData
import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore

@Component
open class DefaultDiskSensors {
    fun getSmartData(hwDiskStore: HWDiskStore): SmartData? {
        return null
    }

    fun getDiskHealth(smartData: SmartData): DeviceHealth? {
        return null
    }
}