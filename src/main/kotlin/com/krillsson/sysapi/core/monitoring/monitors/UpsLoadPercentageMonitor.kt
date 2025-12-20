package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*


class UpsLoadPercentageMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>
) : Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.UPS_LOAD_PERCENTAGE

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        val ups = event.upsDeviceMetrics.firstOrNull { it.id == config.monitoredItemId }
        return ups?.loadPercent?.toNumericalValue()
    }

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        return MonitoredValue.NumericalValue(100)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}