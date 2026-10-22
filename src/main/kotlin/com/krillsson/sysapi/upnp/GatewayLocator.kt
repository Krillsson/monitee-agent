package com.krillsson.sysapi.upnp

import org.bitlet.weupnp.GatewayDevice
import org.bitlet.weupnp.GatewayDiscover
import org.springframework.stereotype.Component

@Component
class GatewayLocator {
    fun find(): GatewayDevice? {
        val gatewayDiscover = GatewayDiscover()
        gatewayDiscover.discover()
        return gatewayDiscover.validGateway
    }
}
