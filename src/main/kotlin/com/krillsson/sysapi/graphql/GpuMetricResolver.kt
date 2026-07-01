package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "GpuMetrics")
class GpuMetricResolver {
    @SchemaMapping
    fun temperature(load: GpuLoad) = load.health.temperature

    @SchemaMapping
    fun fanPercent(load: GpuLoad) = load.health.fanPercent

    @SchemaMapping
    fun powerDraw(load: GpuLoad) = load.health.powerDraw

    @SchemaMapping
    fun coreClockMhz(load: GpuLoad) = load.health.coreClockMhz

    @SchemaMapping
    fun memoryClockMhz(load: GpuLoad) = load.health.memoryClockMhz
}
