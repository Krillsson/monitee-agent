package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class ExternalIpChangedMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {

    companion object {
        val selector: ConditionalValueSelector = { load, _ ->
            val connectivity = load.connectivity
            value(connectivity)
        }

        fun value(connectivity: Connectivity): MonitoredValue.ConditionalValue? {
            val externalIp = connectivity.externalIp
            val previousExternalIp = connectivity.previousExternalIp

            return if (externalIp == null || previousExternalIp == null) {
                true.toConditionalValue()
            } else if (externalIp != previousExternalIp) {
                false.toConditionalValue()
            } else {
                true.toConditionalValue()
            }
        }
    }

    override val type: Type = Type.EXTERNAL_IP_CHANGED

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue? = selector(event.load, null)
    override fun maxValue(info: SystemInfo): MonitoredValue.ConditionalValue? {
        return MonitoredValue.ConditionalValue(true)
    }

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue): Boolean {
        return !value.value
    }
}