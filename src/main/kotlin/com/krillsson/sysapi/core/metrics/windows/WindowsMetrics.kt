package com.krillsson.sysapi.core.metrics.windows

import com.krillsson.sysapi.core.domain.system.OperatingSystem
import com.krillsson.sysapi.core.domain.system.Platform
import com.krillsson.sysapi.core.metrics.MemoryMetrics
import com.krillsson.sysapi.core.metrics.SystemMetrics
import com.krillsson.sysapi.core.metrics.defaultimpl.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
@Lazy
class WindowsMetrics(
    cpuMetrics: WindowsCpuMetrics,
    networkMetrics: DefaultNetworkMetrics,
    gpuMetrics: WindowsGpuMetrics,
    @Qualifier("windowsDiskMetrics") diskMetrics: WindowsDiskMetrics,
    fileSystemMetrics: DefaultFileSystemMetrics,
    processesMetrics: DefaultProcessesMetrics,
    motherboardMetrics: WindowsMotherboardMetrics,
    memoryMetrics: MemoryMetrics,
    operatingSystem: OperatingSystem,
    platform: Platform,
    private val ohmManagerFactory: OHMManager
) : DefaultMetrics(
    cpuMetrics,
    networkMetrics,
    gpuMetrics,
    diskMetrics,
    fileSystemMetrics,
    processesMetrics,
    motherboardMetrics,
    memoryMetrics,
    operatingSystem,
    platform
) {
    override fun initialize() {
        super.initialize()
        if (ohmManagerFactory.prerequisitesFilled()) {
            ohmManagerFactory.initialize()
        }
    }

    fun prerequisitesFilled() = ohmManagerFactory.prerequisitesFilled()
}