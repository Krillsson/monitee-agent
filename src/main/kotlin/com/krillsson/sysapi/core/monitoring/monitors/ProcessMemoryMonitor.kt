package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class ProcessMemoryMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {
    companion object {
        val selector: NumericalValueSelector = { load, monitoredItemID ->
            val pid = monitoredItemID?.toInt()
            load.processes.firstOrNull { it.processID == pid }?.residentSetSize?.toNumericalValue()
        }

        val maxValueSelector: MaxValueNumericalSelector = { input, _ ->
            input.memory.totalBytes.toNumericalValue()
        }
    }

    override val type: Type = Type.PROCESS_MEMORY_SPACE

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, config.monitoredItemId)

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue? = maxValueSelector(input, null)

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}