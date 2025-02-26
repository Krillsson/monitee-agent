package com.krillsson.sysapi.core.metrics.defaultimpl

import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore

@Component
open class DefaultDiskSensors {
    fun getDiskTemperature(hwDiskStore: HWDiskStore): Double? {
        return null
    }
}