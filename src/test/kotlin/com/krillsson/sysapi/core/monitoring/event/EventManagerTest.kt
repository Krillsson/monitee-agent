package com.krillsson.sysapi.core.monitoring.event

import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventManagerTest {

    private val repository = mockk<EventRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC)
    private val config = mockk<YAMLConfigFile>()

    private lateinit var manager: EventManager

    private val survivingMonitorId = UUID.randomUUID()
    private val orphanedMonitorId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { config.metricsConfig } returns MetricsConfiguration()
        every { repository.write(any()) } just Runs
        manager = EventManager(repository, clock, config)
    }

    private fun ongoingEvent(
        monitorId: UUID,
        id: UUID = UUID.randomUUID(),
        severity: EventSeverity = EventSeverity.CRITICAL
    ) = OngoingEvent(
        id = id,
        monitorId = monitorId,
        monitoredItemId = monitorId.toString(),
        monitorType = Monitor.Type.WEBSERVER_UP,
        startTime = Instant.parse("2026-08-01T09:00:00Z"),
        threshold = MonitoredValue.ConditionalValue(true),
        value = MonitoredValue.ConditionalValue(false),
        severity = severity
    )

    private fun pastEvent(monitorId: UUID, id: UUID = UUID.randomUUID()) = PastEvent(
        id = id,
        monitorId = monitorId,
        monitoredItemId = monitorId.toString(),
        startTime = Instant.parse("2026-08-01T08:00:00Z"),
        endTime = Instant.parse("2026-08-01T08:17:00Z"),
        type = Monitor.Type.WEBSERVER_UP,
        threshold = MonitoredValue.ConditionalValue(true),
        endValue = MonitoredValue.ConditionalValue(true),
        startValue = MonitoredValue.ConditionalValue(false),
        severity = EventSeverity.CRITICAL
    )

    @Test
    fun `removes past and ongoing events for monitors that no longer exist`() {
        // Given
        every { repository.read() } returns listOf(
            ongoingEvent(survivingMonitorId),
            pastEvent(survivingMonitorId),
            ongoingEvent(orphanedMonitorId),
            pastEvent(orphanedMonitorId)
        )
        manager.start()

        // When
        val removed = manager.removeEventsForMonitorsNotIn(setOf(survivingMonitorId))

        // Then
        removed shouldBe true
        manager.getAll().map { it.monitorId }.toSet() shouldBe setOf(survivingMonitorId)
        verify { repository.write(match { stored -> stored.none { it.monitorId == orphanedMonitorId } }) }
    }

    @Test
    fun `does nothing when every event still has a live monitor`() {
        // Given
        every { repository.read() } returns listOf(ongoingEvent(survivingMonitorId), pastEvent(survivingMonitorId))
        manager.start()

        // When
        val removed = manager.removeEventsForMonitorsNotIn(setOf(survivingMonitorId))

        // Then
        removed shouldBe false
        manager.getAll().size shouldBe 2
        verify(exactly = 0) { repository.write(any()) }
    }

    @Test
    fun `acknowledges an ongoing event with a note`() {
        // Given
        every { repository.read() } returns emptyList()
        manager.start()
        val event = ongoingEvent(survivingMonitorId)
        manager.add(event)

        // When
        val acknowledged = manager.acknowledge(event.id, "Rebuilding the array")

        // Then
        acknowledged.acknowledgedAt shouldBe clock.instant()
        acknowledged.acknowledgementNote shouldBe "Rebuilding the array"
        manager.ongoingEventById(event.id).shouldNotBeNull().acknowledgedAt shouldBe clock.instant()
        verify { repository.write(match { stored -> stored.single().acknowledgedAt == clock.instant() }) }
    }

    @Test
    fun `unacknowledges an ongoing event`() {
        // Given
        every { repository.read() } returns emptyList()
        manager.start()
        val event = ongoingEvent(survivingMonitorId)
        manager.add(event)
        manager.acknowledge(event.id, "Rebuilding the array")

        // When
        val unacknowledged = manager.unacknowledge(event.id)

        // Then
        unacknowledged.acknowledgedAt shouldBe null
        unacknowledged.acknowledgementNote shouldBe null
    }

    @Test
    fun `refuses to acknowledge an unknown event`() {
        // Given
        every { repository.read() } returns emptyList()
        manager.start()
        val unknownId = UUID.randomUUID()

        // When
        val failure = shouldThrow<IllegalArgumentException> { manager.acknowledge(unknownId, null) }

        // Then
        failure.message shouldBe "No event with id $unknownId"
    }

    @Test
    fun `refuses to acknowledge an event that has already ended`() {
        // Given
        val past = pastEvent(survivingMonitorId)
        every { repository.read() } returns listOf(past)
        manager.start()

        // When
        val failure = shouldThrow<IllegalArgumentException> { manager.acknowledge(past.id, null) }

        // Then
        failure.message shouldBe "Event ${past.id} has already ended"
    }

    @Test
    fun `keeps the acknowledgement when the monitor escalates the event`() {
        // Given
        every { repository.read() } returns emptyList()
        manager.start()
        val id = UUID.randomUUID()
        manager.add(ongoingEvent(survivingMonitorId, id, EventSeverity.WARNING))
        manager.acknowledge(id, "Rebuilding the array")

        // When
        manager.add(ongoingEvent(survivingMonitorId, id, EventSeverity.CRITICAL))

        // Then
        val escalated = manager.ongoingEventById(id).shouldNotBeNull()
        escalated.severity shouldBe EventSeverity.CRITICAL
        escalated.acknowledgedAt shouldBe clock.instant()
        escalated.acknowledgementNote shouldBe "Rebuilding the array"
    }

    @Test
    fun `carries the acknowledgement over to the past event when the monitor resolves it`() {
        // Given
        every { repository.read() } returns emptyList()
        manager.start()
        val id = UUID.randomUUID()
        manager.add(ongoingEvent(survivingMonitorId, id))
        manager.acknowledge(id, "Rebuilding the array")

        // When
        manager.add(pastEvent(survivingMonitorId, id))

        // Then
        val resolved = manager.getAll().single().shouldBeInstanceOf<PastEvent>()
        resolved.acknowledgedAt shouldBe clock.instant()
        resolved.acknowledgementNote shouldBe "Rebuilding the array"
    }

    @Test
    fun `carries the acknowledgement over when an ongoing event is closed by hand`() {
        // Given
        every { repository.read() } returns emptyList()
        manager.start()
        val event = ongoingEvent(survivingMonitorId)
        manager.add(event)
        manager.acknowledge(event.id, "Rebuilding the array")

        // When
        manager.removeOngoingEventsForMonitorId(survivingMonitorId)

        // Then
        manager.getAll().single().shouldBeInstanceOf<PastEvent>().acknowledgementNote shouldBe "Rebuilding the array"
    }

    @Test
    fun `stays notified once a notification went out for the event`() {
        // Given
        every { repository.read() } returns emptyList()
        manager.start()
        val id = UUID.randomUUID()
        manager.add(ongoingEvent(survivingMonitorId, id, EventSeverity.WARNING), notified = false)
        val withheld = manager.ongoingEventById(id).shouldNotBeNull().notified

        // When
        manager.add(ongoingEvent(survivingMonitorId, id, EventSeverity.CRITICAL), notified = true)
        manager.add(ongoingEvent(survivingMonitorId, id, EventSeverity.WARNING), notified = false)

        // Then
        withheld shouldBe false
        manager.ongoingEventById(id).shouldNotBeNull().notified shouldBe true
    }
}
