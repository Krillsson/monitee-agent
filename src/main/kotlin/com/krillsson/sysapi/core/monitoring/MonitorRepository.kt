package com.krillsson.sysapi.core.monitoring

import org.springframework.stereotype.Component

@Component
class MonitorRepository(private val store: MonitorStore, private val monitorFactory: MonitorFactory) {

    fun read(): List<Monitor<MonitoredValue>> {
        return store.read()?.map { monitorFactory.createMonitor(it.type, it.id, it.config.asConfig(it.type)) }.orEmpty()
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
            monitoredItemId, threshold.asDouble(), inertia, warningThreshold?.asDouble()
        )
    }

    private fun <E : Enum<E>> MonitorStore.StoredMonitor.Config.asConfig(type: Monitor.Type): MonitorConfig<MonitoredValue> {
        return MonitorConfig(
            monitoredItemId = monitoredItemId,
            threshold = threshold.asMonitoredValue<E>(type),
            inertia = inertia,
            warningThreshold = warningThreshold?.asMonitoredValue<E>(type)
        )
    }

    private fun <E : Enum<E>> Double.asMonitoredValue(type: Monitor.Type): MonitoredValue {
        return when (type.valueType) {
            Monitor.ValueType.Conditional -> toConditionalValue()
            Monitor.ValueType.Fractional -> toFractionalValue()
            Monitor.ValueType.Numerical -> toNumericalValue()
            Monitor.ValueType.Enum -> toEnumValue(
                requireNotNull(type.toEnumEntries<E>()) { "$type is not mappable to enum entries" }
            )
        }
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