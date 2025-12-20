package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class CpuMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.FractionalValue>) :
    Monitor<MonitoredValue.FractionalValue>() {

    companion object {
        val selector: FractionalValueSelector = { load, _ ->
            val cpuLoad = load.cpuLoad
            value(cpuLoad)
        }

        fun value(cpuLoad: CpuLoad) =
            MonitoredValue.FractionalValue(cpuLoad.usagePercentage.toFloat())

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