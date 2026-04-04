package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class ProcessCpuMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.FractionalValue>) :
    Monitor<MonitoredValue.FractionalValue>() {

    companion object {
        val selector: FractionalValueSelector = { load, monitoredItemID ->
            val pid = monitoredItemID?.toInt()
            load.processes.firstOrNull { it.processID == pid }?.cpuPercent?.toFractionalValue()
        }
        val maxValueSelector: MaxValueFractionalSelector = { input, _ ->
            MonitoredValue.FractionalValue(input.cpuInfo.centralProcessor.logicalProcessorCount.toFloat() * 100f)
        }
    }

    override val type: Type = Type.PROCESS_CPU_LOAD

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? =
        selector(event.load, config.monitoredItemId)

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.FractionalValue? {
        return maxValueSelector(input, null)
    }

    override fun isPastThreshold(value: MonitoredValue.FractionalValue): Boolean {
        return value > config.threshold
    }
}