package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorMetricQueryEvent
import java.util.*

class NetworkUploadRateMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) : Monitor<MonitoredValue.NumericalValue>() {

    companion object {
        val selector: NumericalValueSelector = { load, monitoredItemId ->
            load.networkInterfaceLoads.firstOrNull { n: NetworkInterfaceLoad ->
                n.name.equals(monitoredItemId, ignoreCase = true) || n.mac.equals(monitoredItemId, ignoreCase = true)
            }?.speed?.sendBytesPerSecond?.toNumericalValue()
        }
    }

    override val type: Type = Type.NETWORK_UPLOAD_RATE

    override fun selectValue(event: MonitorMetricQueryEvent): MonitoredValue.NumericalValue? {
        return selector(event.load, config.monitoredItemId)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}