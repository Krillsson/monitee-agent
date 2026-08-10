/*
 * Sys-Api (https://github.com/Krillsson/sys-api)
 *
 * Copyright 2017 Christian Jensen / Krillsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Maintainers:
 * contact[at]christian-jensen[dot]se
 */
package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.connectivity.ConnectivityCheckService
import com.krillsson.sysapi.core.connectivity.InternetServicesCheckService
import com.krillsson.sysapi.core.domain.network.*
import com.krillsson.sysapi.core.metrics.NetworkMetrics
import com.krillsson.sysapi.core.speed.SpeedMeasurementManager.CurrentSpeed
import com.krillsson.sysapi.core.speed.SpeedMeasurementManager.SpeedSource
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import oshi.hardware.HardwareAbstractionLayer
import oshi.hardware.NetworkIF
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.net.SocketException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@Component
open class DefaultNetworkMetrics(
    private val hal: HardwareAbstractionLayer,
    private val speedMeasurementManager: NetworkUploadDownloadRateMeasurementManager,
    private val connectivityCheckService: ConnectivityCheckService,
    private val internetServicesCheckService: InternetServicesCheckService,
    private val containerNetworkInterfaces: ContainerNetworkInterfaces
) : NetworkMetrics {

    private val networkInterfaces = CopyOnWriteArrayList(hal.networkIFs)
    private val speedSources = ConcurrentHashMap<String, NetworkSpeedSource>()

    private val networkInterfaceMetric = Sinks.many()
        .replay()
        .latest<List<NetworkInterfaceLoad>>()

    class NetworkSpeedSource(private val networkIF: NetworkIF) : SpeedSource {

        override fun currentRead(): Long {
            return networkIF.bytesRecv
        }

        override fun currentWrite(): Long {
            return networkIF.bytesSent
        }

        override val name: String
            get() = networkIF.name

        override fun update() {
            networkIF.updateAttributes()
        }
    }

    fun register() {
        val sources = polledInterfaces().map { NetworkSpeedSource(it) }
        sources.forEach { speedSources[it.name] = it }
        speedMeasurementManager.register(sources)
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    fun runMeasurement() {
        speedMeasurementManager.run()
        networkInterfaceMetric.tryEmitNext(networkInterfaceLoads())
    }

    override fun connectivity(): Connectivity {
        return connectivityCheckService.getConnectivity()
    }

    override fun networkInterfaces(): List<NetworkInterface> {
        return polledInterfaces().map { it.asNetworkInterface(isLoopback(it)) }
    }

    override fun networkInterfaceLoads(): List<NetworkInterfaceLoad> {
        return polledInterfaces().map { it.asNetworkInterfaceLoad(isUp(it), speedForInterfaceWithName(it.name)) }
    }

    override fun allNetworkInterfaces(): List<NetworkInterface> {
        return networkInterfaces.map { it.asNetworkInterface(isLoopback(it)) }
    }

    override fun networkInterfaceById(id: String): Optional<NetworkInterface> {
        return Optional.ofNullable(networkInterfaces.firstOrNull {
            it.name.equals(
                id,
                ignoreCase = true
            )
        }
            ?.let { it.asNetworkInterface(isLoopback(it)) })
    }

    override fun allNetworkInterfaceLoads(): List<NetworkInterfaceLoad> {
        return networkInterfaces.map { it.asNetworkInterfaceLoad(isUp(it), speedForInterfaceWithName(it.name)) }
    }

    override fun internetServiceAvailabilities(): List<InternetServicesCheckService.InternetServiceAvailability> {
        return internetServicesCheckService.internetServiceAvailabilities()
    }

    override fun internetServiceAvailabilitiesEvents(): Flux<List<InternetServicesCheckService.InternetServiceAvailability>> {
        return internetServicesCheckService.internetServiceAvailabilitiesEvents()
    }

    override fun networkInterfaceLoadEvents(): Flux<List<NetworkInterfaceLoad>> {
        return networkInterfaceMetric.asFlux()
    }

    override fun networkInterfaceLoadEventsById(id: String): Flux<NetworkInterfaceLoad> {
        return networkInterfaceMetric.asFlux()
            .mapNotNull { list: List<NetworkInterfaceLoad> ->
                list.firstOrNull { n -> n.name.equals(id, ignoreCase = true) }
            }
    }

    override fun networkInterfaceLoadById(id: String): Optional<NetworkInterfaceLoad> {
        return Optional.ofNullable(networkInterfaces.firstOrNull {
            it.name.equals(
                id,
                ignoreCase = true
            )
        }
            ?.let { it.asNetworkInterfaceLoad(isUp(it), speedForInterfaceWithName(it.name)) })
    }

    private fun polledInterfaces(): List<NetworkIF> =
        containerNetworkInterfaces.visible(networkInterfaces) { it.name }

    private fun isUp(networkIF: NetworkIF) = query(networkIF) { it.isUp } ?: false

    private fun isLoopback(networkIF: NetworkIF) = query(networkIF) { it.isLoopback } ?: false

    private fun <T> query(networkIF: NetworkIF, value: (java.net.NetworkInterface) -> T): T? {
        return try {
            value(networkIF.queryNetworkInterface())
        } catch (e: SocketException) {
            forget(networkIF, e)
            null
        }
    }

    private fun forget(networkIF: NetworkIF, e: SocketException) {
        if (networkInterfaces.remove(networkIF)) {
            LOGGER.info("Interface ${networkIF.name} is no longer present, dropping it: ${e.message}")
            speedSources.remove(networkIF.name)?.let { speedMeasurementManager.unregister(it) }
        }
    }

    protected open fun speedForInterfaceWithName(name: String): NetworkInterfaceSpeed {
        val currentSpeedForName = speedMeasurementManager.getCurrentSpeedForName(
            name
        )
        return currentSpeedForName.map { s: CurrentSpeed ->
            NetworkInterfaceSpeed(
                s.readPerSeconds,
                s.writePerSeconds
            )
        }.orElse(EMPTY_INTERFACE_SPEED)
    }

    private fun NetworkIF.asNetworkInterface(isLoopback: Boolean): NetworkInterface =
        NetworkInterface(
            name,
            displayName,
            macaddr,
            speed,
            mtu,
            isLoopback,
            iPv4addr.asList(),
            iPv6addr.asList()
        )

    private fun NetworkIF.asNetworkInterfaceLoad(
        up: Boolean,
        nicSpeed: NetworkInterfaceSpeed
    ): NetworkInterfaceLoad = NetworkInterfaceLoad(
        name,
        macaddr,
        up,
        NetworkInterfaceValues(
            speed,
            bytesRecv,
            bytesSent,
            packetsRecv,
            packetsSent,
            inErrors,
            outErrors
        ),
        nicSpeed
    )

    companion object {
        @JvmField
        val EMPTY_INTERFACE_SPEED = NetworkInterfaceSpeed(0, 0)
        private val LOGGER = LoggerFactory.getLogger(
            DefaultNetworkMetrics::class.java
        )
    }
}