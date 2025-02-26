package com.krillsson.sysapi.core.domain.disk

data class DiskLoad(
    val name: String,
    val serial: String,
    val temperature: Double?,
    val values: DiskValues,
    val speed: DiskSpeed
)