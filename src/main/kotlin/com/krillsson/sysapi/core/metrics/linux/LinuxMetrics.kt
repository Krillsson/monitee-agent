package com.krillsson.sysapi.core.metrics.linux

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
class LinuxMetrics(
    cpuMetrics: LinuxCpuMetrics,
    networkMetrics: DefaultNetworkMetrics,
    @Qualifier("defaultGpuMetrics") gpuMetrics: DefaultGpuMetrics,
    diskMetrics: LinuxDiskMetrics,
    fileSystemMetrics: DefaultFileSystemMetrics,
    processesMetrics: DefaultProcessesMetrics,
    @Qualifier("defaultMotherboardMetrics") motherboardMetrics: DefaultMotherboardMetrics,
    memoryMetrics: MemoryMetrics,
    operatingSystem: OperatingSystem,
    platform: Platform
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
)