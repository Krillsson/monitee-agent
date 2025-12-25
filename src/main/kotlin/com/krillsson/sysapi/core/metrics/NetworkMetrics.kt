package com.krillsson.sysapi.core.metrics

import com.krillsson.sysapi.core.connectivity.InternetServicesCheckService
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterface
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import reactor.core.publisher.Flux
import java.util.*

interface NetworkMetrics {
    fun connectivity(): Connectivity
    fun networkInterfaces(): List<NetworkInterface>
    fun networkInterfaceById(id: String): Optional<NetworkInterface>
    fun networkInterfaceLoads(): List<NetworkInterfaceLoad>

    fun internetServiceAvailabilities(): List<InternetServicesCheckService.InternetServiceAvailability>
    fun internetServiceAvailabilitiesEvents(): Flux<List<InternetServicesCheckService.InternetServiceAvailability>>

    fun networkInterfaceLoadEvents(): Flux<List<NetworkInterfaceLoad>>
    fun networkInterfaceLoadEventsById(id: String): Flux<NetworkInterfaceLoad>
    fun networkInterfaceLoadById(id: String): Optional<NetworkInterfaceLoad>
}