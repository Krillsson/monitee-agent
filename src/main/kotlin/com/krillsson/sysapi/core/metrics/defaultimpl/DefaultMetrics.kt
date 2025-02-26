package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.metrics.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
open class DefaultMetrics(
    @Qualifier("defaultCpuMetrics") private val cpuMetrics: DefaultCpuMetrics,
    private val networkMetrics: DefaultNetworkMetrics,
    @Qualifier("defaultGpuMetrics") private val gpuMetrics: GpuMetrics,
    @Qualifier("defaultDiskMetrics") private val diskMetrics: DefaultDiskMetrics,
    private val fileSystemMetrics: DefaultFileSystemMetrics,
    private val processesMetrics: DefaultProcessesMetrics,
    @Qualifier("defaultMotherboardMetrics") private val motherboardMetrics: MotherboardMetrics,
    private val memoryMetrics: MemoryMetrics,
    private val systemMetrics: SystemMetrics,
) : Metrics {

    override fun initialize() {
        diskMetrics.register()
        networkMetrics.register()
    }

    override fun cpuMetrics(): CpuMetrics {
        return cpuMetrics
    }

    override fun networkMetrics(): NetworkMetrics {
        return networkMetrics
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