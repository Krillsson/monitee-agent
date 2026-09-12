package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorInputCreator
import java.util.*

class DiskWriteRateMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.DISK_WRITE_RATE

    companion object {
        val selector: NumericalValueSelector = { load, monitoredItemId ->
            val diskLoads = load.diskLoads
            value(diskLoads, monitoredItemId)
        }

        fun value(diskLoads: List<DiskLoad>, monitoredItemId: String?) =
            diskLoads.firstOrNull { i: DiskLoad ->
                i.serial.equals(monitoredItemId, ignoreCase = true) || i.name.equals(
                    monitoredItemId,
                    ignoreCase = true
                )
            }?.speed?.writeBytesPerSecond?.toNumericalValue()
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, config.monitoredItemId)

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue? {
        return MonitorInputCreator.THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND.toNumericalValue()
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue, threshold: MonitoredValue.NumericalValue): Boolean {
        return value > threshold
    }
}