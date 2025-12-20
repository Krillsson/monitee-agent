package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class ContainerMemoryMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>
) : Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.CONTAINER_MEMORY_SPACE

    companion object {
        val selector: ContainerNumericalValueSelector = { _, containerMetrics, monitoredItemId ->
            containerMetrics.filter {
                it.id.equals(monitoredItemId, ignoreCase = true)
            }.map {
                it.memoryUsage.usageBytes.toNumericalValue()
            }.firstOrNull()
        }
        val maxValueSelector: MaxValueNumericalSelector = { info, _ ->
            info.memory.totalBytes.toNumericalValue()
        }
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        return selector(event.containers, event.containerStats, config.monitoredItemId)
    }

    override fun maxValue(info: SystemInfo): MonitoredValue.NumericalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}