package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.docker.ImageUpdateStatus
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import java.util.*

class ContainerUpdateAvailableMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>
) : Monitor<MonitoredValue.ConditionalValue>() {
    override val type: Type = Type.CONTAINER_UPDATE_AVAILABLE

    companion object {
        val selector: ContainerUpdateConditionalValueSelector = { updates, monitoredItemId ->
            updates.firstOrNull {
                it.containerId.equals(monitoredItemId, ignoreCase = true)
            }?.let {
                (it.status != ImageUpdateStatus.OUTDATED).toConditionalValue()
            }
        }
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue? {
        return selector(event.containerImageUpdates, config.monitoredItemId)
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.ConditionalValue {
        return MonitoredValue.ConditionalValue(true)
    }

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue): Boolean {
        return !value.value
    }
}
