package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class WebServerUpMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {
    override val type: Type = Type.WEBSERVER_UP

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue? {
        val result = event.checkResults.firstOrNull { it.checkId.toString() == config.monitoredItemId }
        return (result?.successful == true).toConditionalValue()
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.ConditionalValue {
        return MonitoredValue.ConditionalValue(true)
    }

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue): Boolean {
        return !value.value
    }
}