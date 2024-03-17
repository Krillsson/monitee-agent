package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class MemorySpaceMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {
    companion object {
        val selector: NumericalValueSelector = { load, _ ->
            load.memory.availableBytes.toNumericalValue()
        }

        val maxValueSelector: MaxValueNumericalSelector = { info, _ ->
            info.memory.totalBytes.toNumericalValue()
        }
    }

    override val type: Type = Type.MEMORY_SPACE

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, null)

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value < config.threshold
    }
}