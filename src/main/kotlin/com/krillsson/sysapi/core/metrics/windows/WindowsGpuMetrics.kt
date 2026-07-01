package com.krillsson.sysapi.core.metrics.windows

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultGpuLoadMetrics
import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultGpuMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import oshi.hardware.HardwareAbstractionLayer

@Component
class WindowsGpuMetrics(
    hal: HardwareAbstractionLayer,
    @Qualifier("defaultGpuLoadMetrics") gpuLoadMetrics: DefaultGpuLoadMetrics
) : DefaultGpuMetrics(hal, gpuLoadMetrics)
