package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.connectivity.InternetServicesCheckService
import com.krillsson.sysapi.core.domain.cpu.CentralProcessor
import com.krillsson.sysapi.core.domain.gpu.Gpu
import com.krillsson.sysapi.core.domain.motherboard.Motherboard
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.processes.Process
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.graphql.domain.InternetService
import com.krillsson.sysapi.graphql.domain.System
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import oshi.hardware.UsbDevice
import kotlin.jvm.optionals.getOrNull

@Controller
@SchemaMapping(typeName = "Connectivity")
class ConnectivityResolver(val metrics: Metrics) {

    @SchemaMapping
    fun internetServicesAvailability(): List<InternetService> {
        return metrics.networkMetrics().internetServiceAvailabilities().map {
            when(it){
                is InternetServicesCheckService.InternetServiceAvailability.Available -> InternetService(
                    it.id,
                    it.name,
                    it.address,
                    it.port,
                    true,
                    null,
                    it.latencyMs
                )
                is InternetServicesCheckService.InternetServiceAvailability.Unavailable -> InternetService(
                    it.id,
                    it.name,
                    it.address,
                    it.port,
                    false,
                    it.message,
                    -1
                )
            }
        }
    }
}