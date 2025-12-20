package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import java.util.*
import kotlin.enums.EnumEntries

abstract class EnumMonitorBase<E: Enum<E>>(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.EnumValue<E>>,
) : Monitor<MonitoredValue.EnumValue<E>>() {

    abstract val entries: EnumEntries<E>

    // We can't assume that the Enum is ordered by its significance
    open fun orderOfEntry(entry: E) = entries.indexOf(entry)

    override fun maxValue(info: SystemInfo): MonitoredValue.EnumValue<E>? {
        return entries
            .map { orderOfEntry(it) to it }
            .maxBy { (order, _) -> order }
            .second
            .let {  MonitoredValue.EnumValue(it) }
    }

    override fun isPastThreshold(value: MonitoredValue.EnumValue<E>): Boolean {
        val indexOfConfiguredThreshold = orderOfEntry(config.threshold.value)
        val indexOfValue = orderOfEntry(value.value)
        return indexOfValue > indexOfConfiguredThreshold
    }
}