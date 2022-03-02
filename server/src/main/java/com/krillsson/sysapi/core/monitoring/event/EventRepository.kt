package com.krillsson.sysapi.core.monitoring.event

import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.monitor.toConditionalValue
import com.krillsson.sysapi.core.domain.monitor.toFractionalValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.persistence.Store

class EventRepository(private val store: Store<List<EventStore.StoredEvent>>) {

    fun read(): List<Event> {
        return store.read()?.map { it.asEvent() }.orEmpty()
    }

    fun write(value: List<Event>) {
        store.write(value.map { it.asStoredEvent() })
    }

    private fun EventStore.StoredEvent.asEvent(): Event {
        return when (type) {
            EventStore.StoredEvent.Type.ONGOING -> OngoingEvent(
                id = id,
                monitorId = monitorId,
                monitoredItemId = monitoredItemId,
                monitorType = monitorType,
                startTime = startTime,
                threshold = mapValueToMonitorValue { it.threshold },
                value = mapValueToMonitorValue { it.value }
            )
            EventStore.StoredEvent.Type.PAST -> PastEvent(
                id = id,
                monitorId = monitorId,
                monitoredItemId = monitoredItemId,
                startTime = startTime,
                endTime = requireNotNull(endTime) { "endTime is required for past events" },
                type = monitorType,
                threshold = mapValueToMonitorValue { it.threshold },
                value = mapValueToMonitorValue { it.value }
            )
        }
    }

    private fun EventStore.StoredEvent.mapValueToMonitorValue(mapper: (EventStore.StoredEvent) -> Double): MonitoredValue {
        return when(monitorType.valueType) {
            Monitor.ValueType.Numerical -> mapper(this).toNumericalValue()
            Monitor.ValueType.Fractional -> mapper(this).toFractionalValue()
            Monitor.ValueType.Conditional -> mapper(this).toConditionalValue()
        }
    }

    private fun Event.asStoredEvent(): EventStore.StoredEvent {
        return when (this) {
            is PastEvent -> EventStore.StoredEvent(
                id,
                monitorId,
                monitoredItemId,
                startTime,
                endTime,
                monitorType,
                threshold.asDouble(),
                value.asDouble(),
                EventStore.StoredEvent.Type.PAST
            )
            is OngoingEvent -> EventStore.StoredEvent(
                id,
                monitorId,
                monitoredItemId,
                startTime,
                null,
                monitorType,
                threshold.asDouble(),
                value.asDouble(),
                EventStore.StoredEvent.Type.ONGOING
            )
            else -> throw IllegalArgumentException("Unknown event type encountered $this")
        }
    }

    private fun MonitoredValue.asDouble() : Double {
        return when (this) {
            is MonitoredValue.ConditionalValue -> if (value) 1.0 else 0.0
            is MonitoredValue.FractionalValue -> value.toDouble()
            is MonitoredValue.NumericalValue -> value.toDouble()
        }
    }
}