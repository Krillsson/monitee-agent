package com.krillsson.sysapi.core.metrics

import com.krillsson.sysapi.core.domain.cpu.CpuInfo
import com.krillsson.sysapi.core.domain.cpu.CpuLoad

interface CpuMetrics {
    fun cpuInfo(): CpuInfo
    fun cpuLoad(): CpuLoad
    fun uptime(): Long
}