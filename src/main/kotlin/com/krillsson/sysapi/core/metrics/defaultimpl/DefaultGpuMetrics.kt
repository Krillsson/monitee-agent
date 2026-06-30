package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.domain.gpu.Gpu
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.metrics.GpuMetrics
import org.springframework.stereotype.Component
import oshi.hardware.Display
import oshi.hardware.HardwareAbstractionLayer

@Component
open class DefaultGpuMetrics(private val hal: HardwareAbstractionLayer) : GpuMetrics {
    override fun gpuLoads(): List<GpuLoad> {
        return emptyList()
    }

    override fun gpus(): List<Gpu> {
        return emptyList()
    }

    override fun displays(): List<Display> {
        return hal.displays
    }
}