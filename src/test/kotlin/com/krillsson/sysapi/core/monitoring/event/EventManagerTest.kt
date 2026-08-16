package com.krillsson.sysapi.core.monitoring.event

import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import io.kotest.matchers.shouldBe
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

    private fun ongoingEvent(monitorId: UUID) = OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = monitorId,
        monitoredItemId = monitorId.toString(),
        monitorType = Monitor.Type.WEBSERVER_UP,
        startTime = Instant.parse("2026-08-01T09:00:00Z"),
        threshold = MonitoredValue.ConditionalValue(true),
        value = MonitoredValue.ConditionalValue(false)
    )

    private fun pastEvent(monitorId: UUID) = PastEvent(
        id = UUID.randomUUID(),
        monitorId = monitorId,
        monitoredItemId = monitorId.toString(),
        startTime = Instant.parse("2026-08-01T08:00:00Z"),
        endTime = Instant.parse("2026-08-01T08:17:00Z"),
        type = Monitor.Type.WEBSERVER_UP,
        threshold = MonitoredValue.ConditionalValue(true),
        endValue = MonitoredValue.ConditionalValue(true),
        startValue = MonitoredValue.ConditionalValue(false)
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
}
