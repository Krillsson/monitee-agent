package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.NotificationsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import com.krillsson.sysapi.notifications.localization.NotificationFormatter
import com.krillsson.sysapi.notifications.ntfy.NtfyService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test

class NotificationManagerTest {

    private val ntfyService = mockk<NtfyService>(relaxed = true)
    private val notificationFormatter = mockk<NotificationFormatter>()
    private val configFile = mockk<YAMLConfigFile>()

    private val sent = slot<NotificationParameters>()

    private val manager: NotificationManager

    init {
        every { configFile.notifications } returns NotificationsConfiguration(serverName = "nas")
        every { notificationFormatter.formatNotification(any(), any()) } returns ("title" to "message")
        every { ntfyService.enabled } returns true
        every { ntfyService.notify(capture(sent)) } returns Unit
        manager = NotificationManager(
            serverIdService = mockk(relaxed = true),
            ntfyService = ntfyService,
            webhookService = mockk(relaxed = true),
            mqttNotificationService = mockk(relaxed = true),
            notificationFormatter = notificationFormatter,
            configFile = configFile,
            deeplinkCreator = mockk(relaxed = true)
        )
    }

    private fun ongoingEvent(severity: EventSeverity) = Notification.OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = null,
        monitorType = Monitor.Type.CPU_LOAD,
        startTime = Instant.parse("2026-08-01T09:00:00Z"),
        threshold = 90f.toFractionalValue(),
        value = 95f.toFractionalValue(),
        inertia = Duration.ofMinutes(5),
        severity = severity
    )

    @Test
    fun `sends a critical event at a higher priority than a warning`() {
        // When
        manager.notify(ongoingEvent(EventSeverity.WARNING))
        val warningPriority = sent.captured.priority
        manager.notify(ongoingEvent(EventSeverity.CRITICAL))
        val criticalPriority = sent.captured.priority

        // Then
        warningPriority shouldBe 3
        criticalPriority shouldBe 4
    }

    @Test
    fun `passes the severity of an ongoing event on to the services`() {
        // When
        manager.notify(ongoingEvent(EventSeverity.WARNING))

        // Then
        sent.captured.severity shouldBe EventSeverity.WARNING
    }

    @Test
    fun `leaves the severity out of a notification that is not an ongoing event`() {
        // When
        manager.notify(
            Notification.ResolvedEvent(
                id = UUID.randomUUID(),
                monitorId = UUID.randomUUID(),
                monitoredItemId = null,
                monitorType = Monitor.Type.CPU_LOAD,
                startTime = Instant.parse("2026-08-01T09:00:00Z"),
                endTime = Instant.parse("2026-08-01T10:00:00Z"),
                threshold = 90f.toFractionalValue(),
                startValue = 95f.toFractionalValue(),
                endValue = 20f.toFractionalValue()
            )
        )

        // Then
        sent.captured.severity shouldBe null
        sent.captured.priority shouldBe 3
    }
}
