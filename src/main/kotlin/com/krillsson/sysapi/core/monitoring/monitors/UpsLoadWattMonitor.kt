package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.*
import java.util.*


class UpsLoadWattMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>,
    ) : Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.UPS_LOAD_WATT

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        val ups = event.upsDeviceMetrics.firstOrNull { it.id == config.monitoredItemId }
        return ups?.realPowerLoadWatts?.toNumericalValue()
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue? {
        val device = input.upsDevices.firstOrNull { it.id == config.monitoredItemId }
        return device?.realPowerNominalWatts?.toLong()?.toNumericalValue()
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}