package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class NetworkUpMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {
    override val type: Type = Type.NETWORK_UP

    companion object {
        val selector: ConditionalValueSelector = { load, monitoredItemId ->
            val networkInterfaceLoads = load.networkInterfaceLoads
            value(networkInterfaceLoads, monitoredItemId)
        }

        fun value(networkInterfaceLoads: List<NetworkInterfaceLoad>, monitoredItemId: String?) =
            networkInterfaceLoads.firstOrNull { n: NetworkInterfaceLoad ->
                n.name.equals(monitoredItemId, ignoreCase = true) || n.mac.equals(monitoredItemId, ignoreCase = true)
            }?.isUp?.toConditionalValue()
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue? {
        return selector(event.load, config.monitoredItemId)
    }

    override fun maxValue(info: SystemInfo): MonitoredValue.ConditionalValue? = MonitoredValue.ConditionalValue(true)

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue): Boolean {
        return !value.value
    }
}