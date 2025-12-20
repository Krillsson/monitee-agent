package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class CpuTemperatureMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {

    companion object {
        val selector: NumericalValueSelector = { load, _ ->
            val cpuLoad = load.cpuLoad
            value(cpuLoad)
        }

        fun value(cpuLoad: CpuLoad) =
            MonitoredValue.NumericalValue(cpuLoad.cpuHealth.temperatures.firstOrNull()?.toLong() ?: 0)
    }

    override val type: Type = Type.CPU_TEMP

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, null)

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        // have no way of knowing this
        return MonitoredValue.NumericalValue(120)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}