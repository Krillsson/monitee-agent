package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.smart.HealthStatus
import java.util.UUID
import kotlin.enums.EnumEntries

class SmartHealthStatusMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.EnumValue<HealthStatus>>
): EnumMonitorBase<HealthStatus>(id, config) {
    override val type: Type = Type.SMART_HEALTH
    override fun selectValue(event: MonitorInput): MonitoredValue.EnumValue<HealthStatus>? {
        return null
    }

    override fun orderOfEntry(entry: Enum<HealthStatus>): Int {
        return when(entry){

        }
    }

    override val entries: EnumEntries<HealthStatus> = HealthStatus.entries
}