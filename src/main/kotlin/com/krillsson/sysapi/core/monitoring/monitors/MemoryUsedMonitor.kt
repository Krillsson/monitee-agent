package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class MemoryUsedMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {
    companion object {
        val selector: NumericalValueSelector = { load, _ ->
            val memoryLoad = load.memory
            value(memoryLoad)
        }

        fun value(memoryLoad: MemoryLoad) =
            memoryLoad.usedBytes.toNumericalValue()

        val maxValueSelector: MaxValueNumericalSelector = { info, _ ->
            info.memory.totalBytes.toNumericalValue()
        }
    }

    override val type: Type = Type.MEMORY_USED

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, null)

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}