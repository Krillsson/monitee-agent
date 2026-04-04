package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.cpu.CpuInfo
import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.memory.MemoryInfo
import com.krillsson.sysapi.core.domain.network.NetworkInterface
import com.krillsson.sysapi.ups.UpsDevice

data class MonitorMaxValueInput(
    val cpuInfo: CpuInfo,
    val memory: MemoryInfo,
    val fileSystems: List<FileSystem>,
    val networkInterfaces: List<NetworkInterface>,
    val upsDevices: List<UpsDevice>,
)

