package com.krillsson.sysapi.core.monitoring.event

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class EventManager(
    private val repository: EventRepository,
    private val clock: Clock,
    private val config: YAMLConfigFile
) {

    private lateinit var events: MutableList<Event>

    val logger by logger()


    @PostConstruct
    fun start() {
        restore()
        cleanupPastEvents()
    }

    @PreDestroy
    fun stop() {
        endOngoingEvents()
        persist()
    }

    fun add(event: Event) {
        removeOngoingEventForMonitor(event.monitorId)
        events.add(event)
        cleanupPastEvents()
        persist()
    }

    private fun cleanupPastEvents() {
        purgePastEventsOlderThan(config.metricsConfig.history.purging.olderThan, config.metricsConfig.history.purging.unit)
        trimPastEventsSize(100)
    }

    fun getAll(): List<Event> = events

    fun remove(id: UUID): Boolean = events
        .removeAll { it.id == id }
        .also { persist() }

    fun eventsForMonitorId(id: UUID): List<Event> = events.filter { it.monitorId == id }

    fun removeEventsForMonitorId(id: UUID) = events
        .removeAll { it.monitorId == id }
        .also { persist() }

    fun removePastEventsForMonitorId(id: UUID) = events
        .removeAll { event -> event.monitorId == id && event is PastEvent }
        .also { persist() }

    fun removeOngoingEventsForMonitorId(id: UUID): Boolean {
        val ongoingEventsForMonitor: List<OngoingEvent> = events.filter { event -> event.monitorId == id }
            .filterIsInstance<OngoingEvent>()
        return if (ongoingEventsForMonitor.isNotEmpty()) {
            ongoingEventsForMonitor.forEach { event ->
                val past = PastEvent(
                    id = event.id,
                    monitorId = event.monitorId,
                    monitoredItemId = event.monitoredItemId,
                    startTime = event.startTime,
                    endTime = clock.instant(),
                    type = event.monitorType,
                    threshold = event.threshold,
                    endValue = event.value,
                    startValue = event.value
                )
                remove(event.id)
                add(past)
            }
            persist()
            true
        } else {
            false
        }
    }

    /**
     * Points every event of a monitored item at another one. Recreating a Docker container gives it
     * a new id, and an event left behind on the old one deep links to a container the app can no
     * longer resolve. Stopping the container as part of the update is itself likely to raise one.
     */
    fun replaceMonitoredItemId(oldItemId: String, newItemId: String) {
        val affected = events.count { it.monitoredItemId == oldItemId }
        if (affected == 0) {
            return
        }
        logger.info("Moving {} events from {} to {}", affected, oldItemId, newItemId)
        events = events
            .map { event -> if (event.monitoredItemId == oldItemId) event.withMonitoredItemId(newItemId) else event }
            .toMutableList()
        persist()
    }

    private fun Event.withMonitoredItemId(monitoredItemId: String): Event {
        return when (this) {
            is OngoingEvent -> OngoingEvent(
                id = id,
                monitorId = monitorId,
                monitoredItemId = monitoredItemId,
                monitorType = monitorType,
                startTime = startTime,
                threshold = threshold,
                value = value
            )

            is PastEvent -> PastEvent(
                id = id,
                monitorId = monitorId,
                monitoredItemId = monitoredItemId,
                startTime = startTime,
                endTime = endTime,
                type = monitorType,
                threshold = threshold,
                endValue = value,
                startValue = startValue
            )

            else -> this
        }
    }

    private fun persist() = repository.write(events)

    private fun restore() {
        events = repository.read().toMutableList()
    }

    private fun removeOngoingEventForMonitor(monitorId: UUID) {
        events.removeIf { ongoingEvent ->
            ongoingEvent is OngoingEvent && monitorId == ongoingEvent.monitorId
        }
    }

    private fun purgePastEventsOlderThan(olderThan: Long, unit: ChronoUnit) {
        val maxAge = clock.instant().minus(olderThan, unit)
        logger.info("Purging history older than {}", maxAge)
        events.removeAll { event -> event is PastEvent && event.endTime.isBefore(maxAge) }
    }

    private fun trimPastEventsSize(maxSize: Int) {
        val pastEvents = events.filterIsInstance<PastEvent>()
        if (pastEvents.size > maxSize) {
            logger.info("Trimming ${pastEvents.size - maxSize} past events")
            val newPastEvents = pastEvents.takeLast(maxSize)
            events.removeIf { it is PastEvent && !newPastEvents.contains(it) }
        }
    }

    private fun endOngoingEvents() {
        val newEvents: MutableList<Event> = mutableListOf()
        events.forEach { event ->
            if (event is OngoingEvent) {
                logger.info("Shutting down - Ending ongoing event ${event.monitorType} (${event.id})")
                newEvents += PastEvent(
                    event.id,
                    event.monitorId,
                    event.monitoredItemId,
                    event.startTime,
                    clock.instant(),
                    event.monitorType,
                    event.threshold,
                    event.value,
                    event.value
                )
            } else {
                newEvents += event
            }
        }
        events = newEvents
    }
}