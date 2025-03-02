package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.domain.system.OperatingSystem
import com.krillsson.sysapi.core.domain.system.Platform
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.domain.system.SystemLoad
import com.krillsson.sysapi.core.metrics.*
import com.krillsson.sysapi.util.EnvironmentUtils
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
    private val operatingSystem: OperatingSystem,
    private val platform: Platform
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

    override fun systemLoad(sort: ProcessSort, limit: Int): SystemLoad {
        return SystemLoad(
            cpuMetrics.uptime(),
            cpuMetrics.cpuLoad().systemLoadAverage,
            cpuMetrics.cpuLoad(),
            networkMetrics.networkInterfaceLoads(),
            networkMetrics.connectivity(),
            diskMetrics.diskLoads(),
            fileSystemMetrics.fileSystemLoads(),
            memoryMetrics.memoryLoad(),
            processesMetrics.processesInfo(sort, limit).processes,
            gpuMetrics.gpuLoads(),
            motherboardMetrics.motherboardHealth()
        )
    }

    override fun systemInfo(): SystemInfo {
        return SystemInfo(
            hostName = EnvironmentUtils.hostName,
            operatingSystem = operatingSystem,
            platform = platform,
            cpuInfo = cpuMetrics.cpuInfo(),
            motherboard = motherboardMetrics.motherboard(),
            memory = memoryMetrics.memoryInfo(),
            fileSystems = fileSystemMetrics.fileSystems(),
            networkInterfaces = networkMetrics.networkInterfaces(),
            gpus = gpuMetrics.gpus()
        )
    }
}