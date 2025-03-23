package com.krillsson.sysapi.mdns

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.connectivity.ConnectivityCheckService
import com.krillsson.sysapi.util.EnvironmentUtils
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.time.measureTime

@Service
class Mdns(
    private val configuration: YAMLConfigFile,
    private val connectivityCheckService: ConnectivityCheckService,
    private val serverApplicationContext: ServletWebServerApplicationContext,
    private val threadPoolTaskExecutor: ThreadPoolTaskExecutor
) {

    private val logger by logger()

    lateinit var jmdns: JmDNS

    @PostConstruct
    fun start() {
        if (configuration.mDNS.enabled) {
            register()
        }
    }

    fun register() {
        val localIp = connectivityCheckService.findLocalIp()
        val connectorPorts: List<Pair<String, Int>> = connectorPorts()
        jmdns = JmDNS.create(localIp)
        threadPoolTaskExecutor.execute {
            connectorPorts
                // sorts https to be first
                .sortedByDescending { it.first.length }
                .forEach { (scheme, port) ->
                    val serviceType = "_$scheme._tcp.local"
                    val serviceName = EnvironmentUtils.hostName
                    logger.info("Registering mDNS $serviceName $scheme:$port")
                    try {
                        val result = measureTime {
                            val serviceInfo = ServiceInfo.create(serviceType, serviceName, port, "GraphQL at /graphql")
                            jmdns.registerService(serviceInfo)
                        }
                        logger.info("Registered mDNS: $serviceType with name: $serviceName at port $port (took ${result.inWholeMilliseconds}ms)")
                    } catch (e: Exception) {
                        logger.error(
                            "Failed to register mDNS: $serviceType with name: $serviceName at port $port. Message: ${e.message}",
                            e
                        )
                    }
                }
        }
    }

    private fun connectorPorts(): List<Pair<String, Int>> {
        val factory = serverApplicationContext.getBean(TomcatServletWebServerFactory::class.java)
        val additionalConnectors = factory.additionalTomcatConnectors.map { connector ->
            connector.scheme to connector.port
        }
        return listOf("https" to factory.port) + additionalConnectors
    }

    @PreDestroy
    fun stop() {
        if (configuration.mDNS.enabled) {
            jmdns.unregisterAllServices()
        }
    }
}