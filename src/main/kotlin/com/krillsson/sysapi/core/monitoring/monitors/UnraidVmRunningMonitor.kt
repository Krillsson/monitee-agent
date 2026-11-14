package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import java.util.UUID

class UnraidVmRunningMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {
    override val type: Type = Type.UNRAID_VM_RUNNING

    override fun canEvaluate(input: MonitorInput): Boolean = input.unraidVms != null

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue? =
        event.unraidVms?.firstOrNull { it.id == config.monitoredItemId }?.isRunning()?.toConditionalValue()

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.ConditionalValue = MonitoredValue.ConditionalValue(true)

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue, threshold: MonitoredValue.ConditionalValue): Boolean =
        !value.value
}
