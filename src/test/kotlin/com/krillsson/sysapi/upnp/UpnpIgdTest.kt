package com.krillsson.sysapi.upnp

import com.krillsson.sysapi.config.UpnpIgdConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.bitlet.weupnp.GatewayDevice
import org.bitlet.weupnp.PortMappingEntry
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import java.net.InetAddress

class UpnpIgdTest {

    private val port = 8443
    private val thisDevice = "192.168.1.10"

    private val configuration: YAMLConfigFile = mockk {
        every { upnp } returns UpnpIgdConfiguration(enabled = true)
    }
    private val context: ServletWebServerApplicationContext = mockk {
        every { getBean(TomcatServletWebServerFactory::class.java) } returns TomcatServletWebServerFactory(port)
    }
    private val gatewayLocator: GatewayLocator = mockk()

    private fun gateway(mappedTo: String?, mappingSucceeds: Boolean = true): GatewayDevice = mockk(relaxed = true) {
        every { localAddress } returns InetAddress.getByName(thisDevice)
        every { getSpecificPortMappingEntry(port, "TCP", any()) } answers {
            if (mappedTo != null) thirdArg<PortMappingEntry>().internalClient = mappedTo
            mappedTo != null
        }
        every { addPortMapping(port, port, thisDevice, "TCP", any()) } returns mappingSucceeds
    }

    private fun upnpIgd() = UpnpIgd(configuration, context, gatewayLocator)

    @Test
    fun `does not look for a gateway again on stop when startup found none`() {
        // Given
        every { gatewayLocator.find() } returns null
        val upnpIgd = upnpIgd()
        upnpIgd.start()

        // When
        upnpIgd.stop()

        // Then
        verify(exactly = 1) { gatewayLocator.find() }
    }

    @ParameterizedTest
    @CsvSource(
        "'', true, true",
        "'', false, false",
        "192.168.1.10, true, true",
        "192.168.1.99, true, false",
    )
    fun `releases the port on stop only when the mapping belongs to this device`(
        mappedTo: String,
        mappingSucceeds: Boolean,
        released: Boolean
    ) {
        // Given
        val device = gateway(mappedTo.ifEmpty { null }, mappingSucceeds)
        every { gatewayLocator.find() } returns device
        val upnpIgd = upnpIgd()
        upnpIgd.start()

        // When
        upnpIgd.stop()

        // Then
        verify(exactly = if (released) 1 else 0) { device.deletePortMapping(port, "TCP") }
        verify(exactly = 1) { gatewayLocator.find() }
    }

    @Test
    fun `does nothing when UPnP is disabled`() {
        // Given
        every { configuration.upnp } returns UpnpIgdConfiguration(enabled = false)
        val upnpIgd = upnpIgd()

        // When
        upnpIgd.start()
        upnpIgd.stop()

        // Then
        verify(exactly = 0) { gatewayLocator.find() }
    }
}
