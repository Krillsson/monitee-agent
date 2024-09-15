package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class NetworkDownloadRateMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>
) : Monitor<MonitoredValue.NumericalValue>() {

    companion object {
        val selector: NumericalValueSelector = { load, monitoredItemId ->
            val networkInterfaceLoads = load.networkInterfaceLoads
            value(networkInterfaceLoads, monitoredItemId)
        }

        fun value(networkInterfaceLoads: List<NetworkInterfaceLoad>, monitoredItemId: String?) =
            networkInterfaceLoads.firstOrNull { n: NetworkInterfaceLoad ->
                n.name.equals(monitoredItemId, ignoreCase = true) || n.mac.equals(monitoredItemId, ignoreCase = true)
            }?.speed?.receiveBytesPerSecond?.toNumericalValue()

        val maxValueSelector: MaxValueNumericalSelector = { info, monitoredItemId ->
            val nic = info.networkInterfaces.firstOrNull { n ->
                n.name.equals(
                    monitoredItemId,
                    ignoreCase = true
                ) || n.mac.equals(monitoredItemId, ignoreCase = true)
            }
            nic?.let { MonitoredValue.NumericalValue(it.speedBitsPerSeconds / 8) }
        }
    }

    override val type: Type = Type.NETWORK_DOWNLOAD_RATE

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        return maxValueSelector(info, config.monitoredItemId)
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        return selector(event.load, config.monitoredItemId)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}