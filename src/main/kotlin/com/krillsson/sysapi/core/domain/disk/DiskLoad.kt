package com.krillsson.sysapi.core.domain.disk

import com.krillsson.sysapi.smart.DeviceHealth
import com.krillsson.sysapi.smart.SmartData

data class DiskLoad(
    val name: String,
    val serial: String,
    val values: DiskValues,
    val speed: DiskSpeed,
    val smartData: SmartData?,
    val health: DeviceHealth?
) {
    val temperature = smartData?.temperatureCelsius
}