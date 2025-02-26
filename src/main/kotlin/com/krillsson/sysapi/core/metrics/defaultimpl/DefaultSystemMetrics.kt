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
open class DefaultSystemMetrics(
    @Qualifier("defaultCpuMetrics") private val cpuMetrics: CpuMetrics,
    private val networkMetrics: NetworkMetrics,
    @Qualifier("defaultDiskMetrics") private val diskMetrics: DiskMetrics,
    private val fileSystemMetrics: FileSystemMetrics,
    private val memoryMetrics: MemoryMetrics,
    private val processesMetrics: ProcessesMetrics,
    @Qualifier("defaultMotherboardMetrics") private val motherboardMetrics: MotherboardMetrics,
    @Qualifier("defaultGpuMetrics") private val gpuMetrics: GpuMetrics,
    private val operatingSystem: OperatingSystem,
    private val platform: Platform
) : SystemMetrics {

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