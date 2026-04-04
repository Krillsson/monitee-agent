package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*


class UpsOperatingNormallyMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {
    override val type: Type = Type.UPS_OPERATING_NORMALLY

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue? {
        val ups = event.upsDeviceMetrics.firstOrNull { it.id == config.monitoredItemId }
        return ups?.isOperatingNormally()?.toConditionalValue()
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.ConditionalValue? {
        return MonitoredValue.ConditionalValue(true)
    }

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue): Boolean {
        return !value.value
    }
}