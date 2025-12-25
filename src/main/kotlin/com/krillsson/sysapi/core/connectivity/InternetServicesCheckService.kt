package com.krillsson.sysapi.core.connectivity

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@Service
class InternetServicesCheckService(
    yamlConfigFile: YAMLConfigFile

) {
    private val internetServicesLatencyCheckResult = Sinks.many()
        .replay()
        .latest<List<InternetServiceAvailability>>()

    sealed interface InternetServiceAvailability {

        val name: String
        val id: String
        val port: Int
        val address: String

        data class Available(
            override val name: String,
            override val id: String,
            override val port: Int,
            override val address: String,
            val latencyMs: Long
        ) : InternetServiceAvailability

        data class Unavailable(
            override val name: String,
            override val id: String,
            override val port: Int,
            override val address: String,
            val message: String
        ) : InternetServiceAvailability
    }

    private data class TcpConnectResult(
        val connected: Boolean,
        val latencyMs: Long?,
        val errorMessage: String? = null
    ) {
        companion object {
            fun connected(latencyMs: Long) =
                TcpConnectResult(connected = true, latencyMs = latencyMs, errorMessage = null)

            fun disconnected(errorMessage: String?) =
                TcpConnectResult(connected = false, latencyMs = null, errorMessage = errorMessage)
        }
    }

    private val config = yamlConfigFile.internetServicesCheck

    private val logger by logger()

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    fun runMeasurement() {
        if (config.enabled && internetServicesLatencyCheckResult.currentSubscriberCount() > 0) {
            checkServices()
        }
    }

    @PostConstruct
    fun onStartup() {
        if (config.enabled) {
            checkServices()
        }
    }

    fun internetServiceAvailabilities(): List<InternetServiceAvailability>{
        return internetServicesLatencyCheckResult.asFlux().blockFirst().orEmpty()
    }

    fun internetServiceAvailabilitiesEvents(): Flux<List<InternetServiceAvailability>> {
        return internetServicesLatencyCheckResult.asFlux()
    }



    private fun checkServices() {
        val results: List<InternetServiceAvailability> = config.services.map {
            val result = tcpConnect(
                it.address,
                it.port
            )
            if (result.connected) {
                logger.debug("${it.address}:${it.port} is Available (${result.latencyMs}ms)")
                InternetServiceAvailability.Available(
                    name = it.name,
                    id = it.id,
                    port = it.port,
                    address = it.address,
                    latencyMs = result.latencyMs ?: 0L
                )
            } else {
                logger.debug("${it.address}:${it.port} is Unavailable (${result.latencyMs}ms)")
                InternetServiceAvailability.Unavailable(
                    name = it.name,
                    id = it.id,
                    port = it.port,
                    address = it.address,
                    message = result.errorMessage ?: ""
                )
            }
        }
        internetServicesLatencyCheckResult.tryEmitNext(results)
    }

    private fun tcpConnect(
        address: String,
        port: Int = 80,
        timeoutMs: Int = 2_000
    ): TcpConnectResult {
        val startNs = System.nanoTime()
        val socket = Socket()
        return try {
            socket.use {
                it.connect(InetSocketAddress(address, port), timeoutMs)
            }
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000
            TcpConnectResult.connected(latencyMs)
        } catch (e: Exception) {
            val message = when(e){
                is UnknownHostException -> "Unknown host: $address"
                else -> e.message
            }
            TcpConnectResult.disconnected(message)
        }
    }
}