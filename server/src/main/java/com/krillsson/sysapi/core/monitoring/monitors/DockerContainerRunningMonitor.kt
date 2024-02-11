package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.monitor.toConditionalValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.MetricQueryEvent
import com.krillsson.sysapi.core.monitoring.Monitor
import java.util.*

class DockerContainerRunningMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {
    override val type: Type = Type.CONTAINER_RUNNING

    companion object {
        val selector: ContainerConditionalValueSelector = { containers, monitoredItemId ->
            containers.filter {
                it.id.equals(monitoredItemId, ignoreCase = true)
            }.map {
                if (it.state == State.RUNNING) {
                    true.toConditionalValue()
                } else {
                    false.toConditionalValue()
                }
            }.firstOrNull()
        }
    }

    override fun selectValue(event: MetricQueryEvent): MonitoredValue.ConditionalValue? {
        return selector(event.containers, config.monitoredItemId)
    }

    override fun maxValue(info: SystemInfo): MonitoredValue.ConditionalValue? {
        return MonitoredValue.ConditionalValue(true)
    }

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue): Boolean {
        return !value.value
    }
}