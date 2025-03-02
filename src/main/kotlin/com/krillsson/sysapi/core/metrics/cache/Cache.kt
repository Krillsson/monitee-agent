package com.krillsson.sysapi.core.metrics.cache

import com.krillsson.sysapi.config.CacheConfiguration
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.domain.system.OperatingSystem
import com.krillsson.sysapi.core.domain.system.Platform
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.domain.system.SystemLoad
import com.krillsson.sysapi.core.metrics.*
import com.krillsson.sysapi.util.EnvironmentUtils

class Cache private constructor(
    metrics: Metrics,
    cacheConfiguration: CacheConfiguration,
    val platform: Platform,
    val operatingSystem: OperatingSystem
) : Metrics {
    private val cpuMetrics: CpuMetrics = CachingCpuMetrics(metrics.cpuMetrics(), cacheConfiguration)
    private val networkMetrics: NetworkMetrics = CachingNetworkMetrics(metrics.networkMetrics(), cacheConfiguration)
    private val gpuMetrics: GpuMetrics = CachingGpuMetrics(metrics.gpuMetrics(), cacheConfiguration)
    private val fileSystemMetrics: FileSystemMetrics = CachingFileSystemMetrics(metrics.fileSystemMetrics(), cacheConfiguration)
    private val diskMetrics: DiskMetrics = CachingDiskMetrics(metrics.diskMetrics(), cacheConfiguration)
    private val processesMetrics: ProcessesMetrics = CachingProcessesMetrics(metrics.processesMetrics(), cacheConfiguration)
    private val motherboardMetrics: MotherboardMetrics = CachingMotherboardMetrics(metrics.motherboardMetrics(), cacheConfiguration)
    private val memoryMetrics: MemoryMetrics = CachingMemoryMetrics(metrics.memoryMetrics(), cacheConfiguration)

    override fun initialize() {}

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

    companion object {
        fun wrap(
            factory: Metrics,
            cacheConfiguration: CacheConfiguration,
            platform: Platform,
            operatingSystem: OperatingSystem
        ): Metrics {
            return Cache(factory, cacheConfiguration, platform, operatingSystem)
        }
    }
}