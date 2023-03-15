package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.metrics.*

open class DefaultMetrics(
    private val cpuMetrics: DefaultCpuMetrics,
    private val networkMetrics: DefaultNetworkMetrics,
    private val gpuMetrics: GpuMetrics,
    private val driveMetrics: DefaultDriveMetrics,
    private val diskMetrics: DefaultDiskMetrics,
    private val fileSystemMetrics: DefaultFileSystemMetrics,
    private val processesMetrics: DefaultProcessesMetrics,
    private val motherboardMetrics: MotherboardMetrics,
    private val memoryMetrics: MemoryMetrics,
    private val systemMetrics: SystemMetrics,
) : Metrics {

    override fun initialize() {
        cpuMetrics.register()
        diskMetrics.register()
        networkMetrics.register()
        processesMetrics.register()
    }

    override fun cpuMetrics(): CpuMetrics {
        return cpuMetrics
    }

    override fun networkMetrics(): NetworkMetrics {
        return networkMetrics
    }

    override fun driveMetrics(): DriveMetrics {
        return driveMetrics
    }

    override fun fileSystemMetrics(): FileSystemMetrics {
        return fileSystemMetrics
    }

    override fun diskMetrics(): DiskMetrics {
        return diskMetrics
    }

    override fun memoryMetrics(): MemoryMetrics {
        return memoryMetrics
    }

    override fun processesMetrics(): ProcessesMetrics {
        return processesMetrics
    }

    override fun gpuMetrics(): GpuMetrics {
        return gpuMetrics
    }

    override fun motherboardMetrics(): MotherboardMetrics {
        return motherboardMetrics
    }

    override fun systemMetrics(): SystemMetrics {
        return systemMetrics
    }
}