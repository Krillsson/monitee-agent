package com.krillsson.sysapi.core.metrics

interface Metrics {
    fun initialize()
    fun cpuMetrics(): CpuMetrics
    fun networkMetrics(): NetworkMetrics
    fun driveMetrics(): DriveMetrics
    fun fileSystemMetrics(): FileSystemMetrics
    fun diskMetrics(): DiskMetrics
    fun memoryMetrics(): MemoryMetrics
    fun processesMetrics(): ProcessesMetrics
    fun gpuMetrics(): GpuMetrics
    fun motherboardMetrics(): MotherboardMetrics
    fun systemMetrics(): SystemMetrics
}