package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import java.util.UUID

class ProxmoxGuestMemoryMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>
) : Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.PROXMOX_GUEST_MEMORY_USED

    override fun canEvaluate(input: MonitorInput): Boolean = input.proxmoxGuests != null

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        event.proxmoxGuests?.firstOrNull { it.id == config.monitoredItemId }
            ?.let { (it.metrics?.memoryUsage?.usageBytes ?: 0L).toNumericalValue() }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue? =
        input.proxmoxGuests.firstOrNull { it.id == config.monitoredItemId }?.memoryTotalBytes?.toNumericalValue()

    override fun isPastThreshold(value: MonitoredValue.NumericalValue, threshold: MonitoredValue.NumericalValue): Boolean =
        value > threshold
}
