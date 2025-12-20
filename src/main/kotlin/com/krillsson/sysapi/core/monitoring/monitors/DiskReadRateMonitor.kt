package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class DiskReadRateMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.DISK_READ_RATE

    companion object {
        val selector: NumericalValueSelector = { load, monitoredItemId ->
            val diskLoads = load.diskLoads
            value(diskLoads, monitoredItemId)
        }

        fun value(diskLoads: List<DiskLoad>, monitoredItemId: String?) =
            diskLoads.firstOrNull { i ->
                i.serial.equals(monitoredItemId, ignoreCase = true) || i.name.equals(
                    monitoredItemId,
                    ignoreCase = true
                )
            }?.speed?.readBytesPerSecond?.toNumericalValue()
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, config.monitoredItemId)

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        // have no way of knowing this
        return null
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}