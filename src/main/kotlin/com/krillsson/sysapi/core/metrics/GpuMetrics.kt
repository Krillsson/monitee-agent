package com.krillsson.sysapi.core.metrics

import com.krillsson.sysapi.core.domain.gpu.Gpu
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import oshi.hardware.Display
import reactor.core.publisher.Flux

interface GpuMetrics {
    fun gpus(): List<Gpu>
    fun displays(): List<Display>
    fun gpuLoads(): List<GpuLoad>
    fun gpuLoadById(id: String): GpuLoad?
    fun gpuLoadEvents(): Flux<List<GpuLoad>>
    fun gpuLoadEventsById(id: String): Flux<GpuLoad>
}
