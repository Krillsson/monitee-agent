package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.monitor.*
import org.springframework.stereotype.Component

@Component
class MonitorRepository(private val store: MonitorStore) {

    fun read(): List<Monitor<MonitoredValue>> {
        return store.read()?.map { MonitorFactory.createMonitor(it.type, it.id, it.config.asConfig(it.type)) }.orEmpty()
    }

    fun write(content: List<Monitor<MonitoredValue>>) {
        store.write(content.map { it.asStoredMonitor() })
    }

    fun update(action: (List<Monitor<MonitoredValue>>?) -> List<Monitor<MonitoredValue>>) {
        val previousValue = read()
        val newValue = action(previousValue)
        write(newValue)
    }

    private fun Monitor<MonitoredValue>.asStoredMonitor(): MonitorStore.StoredMonitor {
        return MonitorStore.StoredMonitor(id, type, config.asStoredMonitorConfig())
    }

    private fun MonitorConfig<out MonitoredValue>.asStoredMonitorConfig(): MonitorStore.StoredMonitor.Config {
        return MonitorStore.StoredMonitor.Config(
            monitoredItemId, threshold.asDouble(), inertia
        )
    }

    private fun MonitorStore.StoredMonitor.Config.asConfig(type: Monitor.Type): MonitorConfig<MonitoredValue> {
        val convertedValue = when (type.valueType) {
            Monitor.ValueType.Conditional -> threshold.toConditionalValue()
            Monitor.ValueType.Fractional -> threshold.toFractionalValue()
            Monitor.ValueType.Numerical -> threshold.toNumericalValue()
            else -> throw IllegalStateException("No equivalent to $this exists in ${Monitor.Type::class.simpleName}")
        }
        return MonitorConfig(
            monitoredItemId, convertedValue, inertia
        )
    }

    private fun MonitoredValue.asDouble(): Double {
        return when (this) {
            is MonitoredValue.ConditionalValue -> if (value) 1.0 else 0.0
            is MonitoredValue.FractionalValue -> value.toDouble()
            is MonitoredValue.NumericalValue -> value.toDouble()
            is MonitoredValue.EnumValue<*> -> value.ordinal.toDouble()
        }
    }
}