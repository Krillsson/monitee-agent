package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.smart.HealthStatus
import java.util.*
import kotlin.enums.EnumEntries

class DiskSmartHealthMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.EnumValue<HealthStatus>>
) : EnumMonitorBase<HealthStatus>(id, config) {

    companion object {
        val selector: EnumValueSelector<HealthStatus> = { load, monitoredItemId ->
            val diskLoads = load.diskLoads
            value(diskLoads, monitoredItemId)
        }

        fun value(diskLoads: List<DiskLoad>, monitoredItemId: String?) =
            diskLoads.firstOrNull { i: DiskLoad ->
                i.serial.equals(monitoredItemId, ignoreCase = true) || i.name.equals(
                    monitoredItemId,
                    ignoreCase = true
                )
            }?.health?.status?.let { MonitoredValue.EnumValue(it) }
    }

    override val type: Type = Type.DISK_SMART_HEALTH
    override fun selectValue(event: MonitorInput): MonitoredValue.EnumValue<HealthStatus>? {
        return selector(event.load, config.monitoredItemId)
    }

    override val entries: EnumEntries<HealthStatus> = HealthStatus.entries
}