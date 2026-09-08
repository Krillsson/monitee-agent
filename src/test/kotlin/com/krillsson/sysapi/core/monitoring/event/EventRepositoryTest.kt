package com.krillsson.sysapi.core.monitoring.event

import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.monitoring.MONITOR_ID
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.jupiter.api.Test

class EventRepositoryTest {

    private val store = mockk<EventStore>(relaxed = true)
    private val repository = EventRepository(store)

    private val eventId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val startTime: OffsetDateTime = OffsetDateTime.of(2026, 8, 1, 9, 0, 0, 0, ZoneOffset.UTC)
    private val endTime: OffsetDateTime = startTime.plusHours(1)

    private fun storedEvent(
        type: EventStore.StoredEvent.Type,
        severity: EventSeverity?
    ) = EventStore.StoredEvent(
        id = eventId,
        monitorId = MONITOR_ID,
        monitoredItemId = "sda",
        startTime = startTime,
        endTime = if (type == EventStore.StoredEvent.Type.PAST) endTime else null,
        monitorType = Monitor.Type.CPU_TEMP,
        threshold = 80.0,
        value = 95.0,
        startValue = 90.0,
        type = type,
        severity = severity
    )

    @Test
    fun `reads an ongoing event stored before severity existed as critical`() {
        // Given
        every { store.read() } returns listOf(storedEvent(EventStore.StoredEvent.Type.ONGOING, severity = null))

        // When
        val event = repository.read().single()

        // Then
        event.shouldBeInstanceOf<OngoingEvent>().severity shouldBe EventSeverity.CRITICAL
    }

    @Test
    fun `reads a past event stored before severity existed as critical`() {
        // Given
        every { store.read() } returns listOf(storedEvent(EventStore.StoredEvent.Type.PAST, severity = null))

        // When
        val event = repository.read().single()

        // Then
        event.shouldBeInstanceOf<PastEvent>().severity shouldBe EventSeverity.CRITICAL
    }

    @Test
    fun `reads back the stored severity when there is one`() {
        // Given
        every { store.read() } returns listOf(
            storedEvent(EventStore.StoredEvent.Type.ONGOING, severity = EventSeverity.WARNING)
        )

        // When
        val event = repository.read().single()

        // Then
        event.severity shouldBe EventSeverity.WARNING
    }

    @Test
    fun `writes the severity of an ongoing event`() {
        // Given
        val written = slot<List<EventStore.StoredEvent>>()
        every { store.write(capture(written)) } returns Unit

        // When
        repository.write(
            listOf(
                OngoingEvent(
                    id = eventId,
                    monitorId = MONITOR_ID,
                    monitoredItemId = "sda",
                    monitorType = Monitor.Type.CPU_TEMP,
                    startTime = Instant.parse("2026-08-01T09:00:00Z"),
                    threshold = 80L.toNumericalValue(),
                    value = 95L.toNumericalValue(),
                    severity = EventSeverity.WARNING
                )
            )
        )

        // Then
        written.captured.single().severity shouldBe EventSeverity.WARNING
    }
}
