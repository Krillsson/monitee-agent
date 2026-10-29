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
import java.time.Instant
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

    fun add(event: Event, notified: Boolean = true) {
        val previous = ongoingEventById(event.id)
        removeOngoingEventForMonitor(event.monitorId)
        events.add(event.continuing(previous, notified))
        cleanupPastEvents()
        persist()
    }

    fun ongoingEventById(id: UUID): OngoingEvent? = events.filterIsInstance<OngoingEvent>().firstOrNull { it.id == id }

    fun acknowledge(id: UUID, note: String?): OngoingEvent {
        logger.info("Acknowledging event {}", id)
        return replaceOngoingEvent(id) { it.copy(acknowledgedAt = clock.instant(), acknowledgementNote = note) }
    }

    fun unacknowledge(id: UUID): OngoingEvent {
        logger.info("Clearing the acknowledgement of event {}", id)
        return replaceOngoingEvent(id) { it.copy(acknowledgedAt = null, acknowledgementNote = null) }
    }

    private fun replaceOngoingEvent(id: UUID, change: (OngoingEvent) -> OngoingEvent): OngoingEvent {
        val index = events.indexOfFirst { it.id == id }
        require(index >= 0) { "No event with id $id" }
        val event = events[index]
        require(event is OngoingEvent) { "Event $id has already ended" }
        return change(event).also {
            events[index] = it
            persist()
        }
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

    fun removeEventsForMonitorsNotIn(existingMonitorIds: Set<UUID>): Boolean {
        val orphaned = events.count { it.monitorId !in existingMonitorIds }
        if (orphaned == 0) {
            return false
        }
        logger.info("Removing {} events referencing monitors that no longer exist", orphaned)
        return events.removeAll { it.monitorId !in existingMonitorIds }.also { persist() }
    }

    fun removePastEventsForMonitorId(id: UUID) = events
        .removeAll { event -> event.monitorId == id && event is PastEvent }
        .also { persist() }

    fun removeOngoingEventsForMonitorId(id: UUID): Boolean {
        val ongoingEventsForMonitor: List<OngoingEvent> = events.filter { event -> event.monitorId == id }
            .filterIsInstance<OngoingEvent>()
        return if (ongoingEventsForMonitor.isNotEmpty()) {
            ongoingEventsForMonitor.forEach { event ->
                remove(event.id)
                add(event.endedNow())
            }
            persist()
            true
        } else {
            false
        }
    }

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

    private fun Event.continuing(previous: OngoingEvent?, notified: Boolean): Event {
        if (previous == null) {
            return if (this is OngoingEvent) copy(notified = notified) else this
        }
        return when (this) {
            is OngoingEvent -> copy(
                acknowledgedAt = previous.acknowledgedAt,
                acknowledgementNote = previous.acknowledgementNote,
                notified = notified || previous.notified
            )

            is PastEvent -> copy(
                acknowledgedAt = previous.acknowledgedAt,
                acknowledgementNote = previous.acknowledgementNote
            )

            else -> this
        }
    }

    private fun Event.withMonitoredItemId(monitoredItemId: String): Event {
        return when (this) {
            is OngoingEvent -> copy(monitoredItemId = monitoredItemId)
            is PastEvent -> copy(monitoredItemId = monitoredItemId)
            else -> this
        }
    }

    private fun OngoingEvent.copy(
        monitoredItemId: String? = this.monitoredItemId,
        acknowledgedAt: Instant? = this.acknowledgedAt,
        acknowledgementNote: String? = this.acknowledgementNote,
        notified: Boolean = this.notified
    ) = OngoingEvent(
        id = id,
        monitorId = monitorId,
        monitoredItemId = monitoredItemId,
        monitorType = monitorType,
        startTime = startTime,
        threshold = threshold,
        value = value,
        severity = severity,
        acknowledgedAt = acknowledgedAt,
        acknowledgementNote = acknowledgementNote,
        notified = notified
    )

    private fun PastEvent.copy(
        monitoredItemId: String? = this.monitoredItemId,
        acknowledgedAt: Instant? = this.acknowledgedAt,
        acknowledgementNote: String? = this.acknowledgementNote
    ) = PastEvent(
        id = id,
        monitorId = monitorId,
        monitoredItemId = monitoredItemId,
        startTime = startTime,
        endTime = endTime,
        type = monitorType,
        threshold = threshold,
        endValue = value,
        startValue = startValue,
        severity = severity,
        acknowledgedAt = acknowledgedAt,
        acknowledgementNote = acknowledgementNote
    )

    private fun OngoingEvent.endedNow() = PastEvent(
        id = id,
        monitorId = monitorId,
        monitoredItemId = monitoredItemId,
        startTime = startTime,
        endTime = clock.instant(),
        type = monitorType,
        threshold = threshold,
        endValue = value,
        startValue = value,
        severity = severity,
        acknowledgedAt = acknowledgedAt,
        acknowledgementNote = acknowledgementNote
    )

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
                newEvents += event.endedNow()
            } else {
                newEvents += event
            }
        }
        events = newEvents
    }
}