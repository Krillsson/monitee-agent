package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import java.util.*

class CheckLatencyMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>
) : Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.CHECK_LATENCY

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        val result = event.checkResults.firstOrNull { it.checkId.toString() == config.monitoredItemId }
        return result?.takeIf { it.successful }?.latencyMs?.toNumericalValue()
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue? {
        val check = input.checks.firstOrNull { it.id.toString() == config.monitoredItemId }
        return check?.timeoutSeconds?.times(1000L)?.toNumericalValue()
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}
