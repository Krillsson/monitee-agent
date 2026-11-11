package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import java.util.UUID

class ProxmoxGuestRunningMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {
    override val type: Type = Type.PROXMOX_GUEST_RUNNING

    override fun canEvaluate(input: MonitorInput): Boolean = input.proxmoxGuests != null

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue? =
        event.proxmoxGuests?.firstOrNull { it.id == config.monitoredItemId }
            ?.let { (it.status == ProxmoxGuestStatus.RUNNING).toConditionalValue() }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.ConditionalValue = MonitoredValue.ConditionalValue(true)

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue, threshold: MonitoredValue.ConditionalValue): Boolean =
        !value.value
}
