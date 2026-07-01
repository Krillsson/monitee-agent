package com.krillsson.sysapi.core.domain.gpu

class GpuLoad(
    val id: String,
    val name: String,
    val coreLoad: Double,
    val vramUsedBytes: Long,
    val vramTotalBytes: Long,
    val health: GpuHealth
)
