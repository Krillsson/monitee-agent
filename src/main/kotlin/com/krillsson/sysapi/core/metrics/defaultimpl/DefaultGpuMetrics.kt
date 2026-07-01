package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.domain.gpu.Gpu
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.metrics.GpuMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import oshi.hardware.Display
import oshi.hardware.HardwareAbstractionLayer
import reactor.core.publisher.Flux

@Component
open class DefaultGpuMetrics(
    private val hal: HardwareAbstractionLayer,
    @Qualifier("defaultGpuLoadMetrics") private val gpuLoadMetrics: DefaultGpuLoadMetrics
) : GpuMetrics {
    override fun gpus(): List<Gpu> {
        return hal.graphicsCards.map { card ->
            Gpu(card.deviceId, card.name, card.vendor, card.versionInfo, card.vRam)
        }
    }

    override fun gpuLoads(): List<GpuLoad> {
        return gpuLoadMetrics.gpuLoads
    }

    override fun gpuLoadById(id: String): GpuLoad? {
        return gpuLoadMetrics.gpuLoadById(id)
    }

    override fun gpuLoadEvents(): Flux<List<GpuLoad>> {
        return gpuLoadMetrics.gpuLoadEvents()
    }

    override fun gpuLoadEventsById(id: String): Flux<GpuLoad> {
        return gpuLoadMetrics.gpuLoadEventsById(id)
    }

    override fun displays(): List<Display> {
        return hal.displays
    }
}
