package com.krillsson.sysapi.core.metrics

import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.domain.system.SystemLoad

interface Metrics {
    fun initialize()
    fun cpuMetrics(): CpuMetrics
    fun networkMetrics(): NetworkMetrics
    fun fileSystemMetrics(): FileSystemMetrics
    fun diskMetrics(): DiskMetrics
    fun memoryMetrics(): MemoryMetrics
    fun processesMetrics(): ProcessesMetrics
    fun gpuMetrics(): GpuMetrics
    fun motherboardMetrics(): MotherboardMetrics
    fun systemLoad(
        sort: ProcessSort = ProcessSort.MEMORY,
        limit: Int = -1
    ): SystemLoad

    fun systemInfo(): SystemInfo
}