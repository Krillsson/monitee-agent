package com.krillsson.sysapi.upnp

import com.krillsson.sysapi.config.SysAPIConfiguration
import com.krillsson.sysapi.util.logger
import io.dropwizard.jetty.HttpsConnectorFactory
import io.dropwizard.lifecycle.Managed
import io.dropwizard.server.DefaultServerFactory
import io.dropwizard.server.SimpleServerFactory
import org.bitlet.weupnp.GatewayDevice
import org.bitlet.weupnp.GatewayDiscover
import org.bitlet.weupnp.PortMappingEntry
import java.net.InetAddress


class UpnpIgd(
    private val configuration: SysAPIConfiguration
) : Managed {

    private val logger by logger()

    companion object {
        private const val PROTOCOL = "TCP"
    }

    override fun start() {
        if (configuration.upnp.enabled) {
            try {
                httpsPort()?.let { port ->
                    getActiveGateway()?.let { activeGW ->
                        logger.info("Attempting to open $port using UPnP-IGD")
                        val localAddress = activeGW.localAddress
                        val availability = checkPortAvailability(activeGW, port)
                        if (availability == PortMappingAvailability.Available) {
                            requestPortMapping(activeGW, port, localAddress)
                        }
                    }
                }
            } catch (exception: Exception) {
                logger.error("Error occurred while performing UPnP-IGD mapping", exception)
            }
        }
    }

    private fun requestPortMapping(
        activeGW: GatewayDevice,
        port: Int,
        localAddress: InetAddress
    ) {
        logger.info("Requesting static lease for ${localAddress.hostAddress} internal: $PROTOCOL/$port external: $PROTOCOL/$port")
        val mappingResult = activeGW.addPortMapping(
            /* externalPort = */ port,
            /* internalPort = */ port,
            /* internalClient = */ localAddress.hostAddress,
            /* protocol = */ PROTOCOL,
            /* description = */ "sys-API https port"
        )
        if (mappingResult) {
            logger.info("Mapping request successful")
        } else {
            logger.info("Mapping request failed")
        }
    }

    enum class PortMappingAvailability {
        Available,
        AlreadyMappedToThisDevice,
        AlreadyMappedToAnotherDevice
    }

    private fun checkPortAvailability(activeGW: GatewayDevice, port: Int): PortMappingAvailability {
        logger.info("Querying gateway availability for port $port")
        val localAddress = activeGW.localAddress
        val portMapping = PortMappingEntry()
        val alreadyMapped = activeGW.getSpecificPortMappingEntry(port, PROTOCOL, portMapping)
        val mappedToThisDevice = portMapping.internalClient == localAddress.hostAddress
        return when {
            alreadyMapped && mappedToThisDevice -> {
                logger.info("Port $port is already mapped to this device")
                PortMappingAvailability.AlreadyMappedToThisDevice
            }

            alreadyMapped -> {
                logger.info("Port $port is already mapped to another device")
                PortMappingAvailability.AlreadyMappedToAnotherDevice
            }

            else -> {
                logger.info("Port $port is available")
                PortMappingAvailability.Available
            }
        }
    }

    private fun getActiveGateway(): GatewayDevice? {
        val gatewayDiscover = GatewayDiscover()
        gatewayDiscover.discover()
        val activeGW = gatewayDiscover.validGateway
        return if (null != activeGW) {
            logger.info("Using gateway: " + activeGW.friendlyName)
            activeGW
        } else {
            logger.info("No active gateway device found")
            null
        }
    }

    override fun stop() {
        if (configuration.upnp.enabled) {
            try {
                httpsPort()?.let { port ->
                    getActiveGateway()?.let { device ->
                        val availability = checkPortAvailability(device, port)
                        if (availability == PortMappingAvailability.AlreadyMappedToThisDevice) {
                            logger.info("Releasing static lease to $port")
                            device.deletePortMapping(port, PROTOCOL)
                        }
                    }
                }
            } catch (exception: Exception) {
                logger.error("Error occurred while releasing UPnP-IGD mapping", exception)
            }
        }
    }

    private fun httpsPort(): Int? = connectorFactories().firstNotNullOfOrNull { factory ->
        when (factory) {
            is HttpsConnectorFactory -> factory.port
            else -> null
        }
    }

    private fun connectorFactories() =
        ((configuration.serverFactory as? DefaultServerFactory)?.applicationConnectors.orEmpty() + (configuration.serverFactory as? SimpleServerFactory)?.connector)
}