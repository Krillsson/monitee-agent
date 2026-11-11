package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import java.util.UUID

class ProxmoxGuestCpuMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.FractionalValue>
) : Monitor<MonitoredValue.FractionalValue>() {
    override val type: Type = Type.PROXMOX_GUEST_CPU_LOAD

    override fun canEvaluate(input: MonitorInput): Boolean = input.proxmoxGuests != null

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? =
        event.proxmoxGuests?.firstOrNull { it.id == config.monitoredItemId }
            ?.let { (it.metrics?.cpuUsage?.usagePercentTotal ?: 0.0).toFractionalValue() }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.FractionalValue = MonitoredValue.FractionalValue(100f)

    override fun isPastThreshold(value: MonitoredValue.FractionalValue, threshold: MonitoredValue.FractionalValue): Boolean =
        value > threshold
}
