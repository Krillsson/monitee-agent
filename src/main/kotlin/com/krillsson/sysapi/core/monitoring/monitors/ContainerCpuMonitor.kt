package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class ContainerCpuMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.FractionalValue>
) : Monitor<MonitoredValue.FractionalValue>() {
    override val type: Type = Type.CONTAINER_CPU_LOAD

    companion object {
        val selector: ContainerFractionalValueSelector = { _, containerMetrics, monitoredItemId ->
            containerMetrics.filter {
                it.id.equals(monitoredItemId, ignoreCase = true)
            }.map {
                it.cpuUsage.usagePercentPerCore.toFractionalValue()
            }.firstOrNull()
        }
        val maxValueSelector: MaxValueFractionalSelector = { info, _ ->
            MonitoredValue.FractionalValue(info.cpuInfo.centralProcessor.logicalProcessorCount.toFloat() * 100f)
        }
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? {
        return selector(event.containers, event.containerStats, config.monitoredItemId)
    }

    override fun maxValue(info: SystemInfo): MonitoredValue.FractionalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.FractionalValue): Boolean {
        return value > config.threshold
    }
}