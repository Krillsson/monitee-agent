package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.notifications.Notification
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class NotificationFormatterTest {

    private val ongoingEventFormatter: OngoingEventFormatter = mockk()
    private val formatter = NotificationFormatter(GenericEventFormatter(), ongoingEventFormatter, mockk())

    private fun ongoingEvent(monitorType: Monitor.Type) = Notification.OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = null,
        monitorType = monitorType,
        startTime = Instant.parse("2026-09-08T23:00:00Z"),
        threshold = MonitoredValue.FractionalValue(0.9f),
        warningThreshold = null,
        value = MonitoredValue.FractionalValue(0.95f),
        inertia = Duration.ZERO,
        severity = EventSeverity.CRITICAL
    )

    @Test
    fun `lists every outstanding event in the quiet hours summary`() {
        // Given
        val cpu = ongoingEvent(Monitor.Type.CPU_LOAD)
        val memory = ongoingEvent(Monitor.Type.MEMORY_USED)
        every { ongoingEventFormatter.formatOngoingEventDescription(cpu) } returns "Load went above 90% to 95%"
        every { ongoingEventFormatter.formatOngoingEventDescription(memory) } returns "Memory usage went above 90% to 95%"

        // When
        val (title, message) = formatter.formatNotification(
            Notification.GenericEvent.QuietHoursSummary(Instant.parse("2026-09-09T07:00:00Z"), listOf(cpu, memory)),
            "MyServer"
        )

        // Then
        title shouldBe "While quiet on MyServer"
        message shouldBe "Load went above 90% to 95%\nMemory usage went above 90% to 95%"
    }
}
