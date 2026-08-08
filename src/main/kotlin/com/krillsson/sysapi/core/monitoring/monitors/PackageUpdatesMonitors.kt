package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import java.util.*

const val MAX_PENDING_UPDATES = 100L

class PackageUpdatesMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>
) : Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.PACKAGE_UPDATES

    companion object {
        val selector: SystemUpdatesNumericalValueSelector = { updates, _ ->
            updates?.totalCount?.toNumericalValue()
        }
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        return selector(event.systemUpdates, config.monitoredItemId)
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue {
        return MAX_PENDING_UPDATES.toNumericalValue()
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}

class PackageSecurityUpdatesMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>
) : Monitor<MonitoredValue.NumericalValue>() {
    override val type: Type = Type.PACKAGE_SECURITY_UPDATES

    companion object {
        val selector: SystemUpdatesNumericalValueSelector = { updates, _ ->
            updates?.securityCount?.toNumericalValue()
        }
    }

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        return selector(event.systemUpdates, config.monitoredItemId)
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue {
        return MAX_PENDING_UPDATES.toNumericalValue()
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}
