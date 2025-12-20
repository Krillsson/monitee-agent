package com.krillsson.sysapi.config

data class SmartConfig(
    val additionalDeviceFlags: List<AdditionalDeviceFlags> = emptyList()
)

data class AdditionalDeviceFlags(
    val device: String,
    val flags: String
)