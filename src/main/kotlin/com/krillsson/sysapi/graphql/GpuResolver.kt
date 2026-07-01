package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.gpu.Gpu
import com.krillsson.sysapi.core.metrics.Metrics
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "Gpu")
class GpuResolver(val metrics: Metrics) {
    @SchemaMapping
    fun metrics(gpu: Gpu) = metrics.gpuMetrics().gpuLoadById(gpu.id)
}
