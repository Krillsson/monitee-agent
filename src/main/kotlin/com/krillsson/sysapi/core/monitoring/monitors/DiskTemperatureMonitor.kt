package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class DiskTemperatureMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {

    companion object {
        val selector: NumericalValueSelector = { load, monitoredItemId ->
            val diskLoads = load.diskLoads
            value(diskLoads, monitoredItemId)
        }
        fun value(diskLoads: List<DiskLoad>, monitoredItemId: String?): MonitoredValue.NumericalValue? {
            val firstOrNull = diskLoads.firstOrNull { i ->
                i.name == monitoredItemId
            }
            return firstOrNull?.temperature?.toNumericalValue()
        }
    }

    override val type: Type = Type.DISK_TEMPERATURE

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, config.monitoredItemId)

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        // have no way of knowing this
        return MonitoredValue.NumericalValue(120)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}