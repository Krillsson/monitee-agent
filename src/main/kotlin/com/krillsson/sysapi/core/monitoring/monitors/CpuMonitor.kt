package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class CpuMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.FractionalValue>) :
    Monitor<MonitoredValue.FractionalValue>() {

    companion object {
        val selector: FractionalValueSelector = { load, _ ->
            MonitoredValue.FractionalValue(load.cpuLoad.usagePercentage.toFloat())
        }
        val maxValueSelector: MaxValueFractionalSelector = { info, _ ->
            MonitoredValue.FractionalValue(info.cpuInfo.centralProcessor.logicalProcessorCount.toFloat() * 100f)
        }
    }

    override val type: Type = Type.CPU_LOAD

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? =
        selector(event.load, null)

    override fun maxValue(info: SystemInfo): MonitoredValue.FractionalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.FractionalValue): Boolean {
        return value > config.threshold
    }
}