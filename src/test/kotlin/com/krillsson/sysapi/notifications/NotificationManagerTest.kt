package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.NotificationsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.mqtt.MqttNotificationService
import com.krillsson.sysapi.notifications.localization.NotificationFormatter
import com.krillsson.sysapi.notifications.ntfy.NtfyService
import com.krillsson.sysapi.notifications.webhook.WebhookService
import com.krillsson.sysapi.persistence.KeyValueRepository
import com.krillsson.sysapi.serverid.ServerIdService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationManagerTest {

    private val store = mutableMapOf<String, String>()
    private var now = Instant.parse("2026-09-08T12:00:00Z")
    private lateinit var clock: Clock
    private lateinit var snoozeNotificationsService: SnoozeNotificationsService

    private val sent = slot<NotificationParameters>()

    private val ntfyService: NtfyService = mockk(relaxed = true) { every { enabled } returns true }
    private val webhookService: WebhookService = mockk(relaxed = true) { every { enabled } returns true }
    private val mqttNotificationService: MqttNotificationService = mockk(relaxed = true) { every { enabled } returns true }
    private val notificationFormatter: NotificationFormatter = mockk {
        every { formatNotification(any(), any()) } returns ("title" to "message")
    }
    private val deeplinkCreator: DeeplinkCreator = mockk {
        every { createDeeplink(any()) } returns "https://monitee.app/server/id/monitor/id"
        every { snooze() } returns "https://monitee.app/server/id/snooze"
    }
    private val serverIdService: ServerIdService = mockk {
        every { serverId } returns UUID.fromString("11111111-2222-3333-4444-555555555555")
    }
    private val configFile: YAMLConfigFile = mockk {
        every { notifications } returns NotificationsConfiguration(serverName = "test-server")
    }

    private lateinit var manager: NotificationManager

    @BeforeEach
    fun setUp() {
        every { ntfyService.notify(capture(sent)) } returns Unit

        store.clear()
        val repository = mockk<KeyValueRepository>()
        every { repository.get(any()) } answers { store[firstArg()] }
        every { repository.put(any(), anyNullable()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String?>()
            if (value != null) store[key] = value else store.remove(key)
        }
        every { repository.remove(any()) } answers { store.remove(firstArg<String>()) != null }

        clock = mockk()
        every { clock.instant() } answers { now }
        every { clock.zone } returns ZoneOffset.UTC

        snoozeNotificationsService = SnoozeNotificationsService(repository, clock)

        manager = NotificationManager(
            serverIdService,
            ntfyService,
            webhookService,
            mqttNotificationService,
            notificationFormatter,
            configFile,
            deeplinkCreator,
            snoozeNotificationsService
        )
    }

    private fun ongoingEvent(severity: EventSeverity = EventSeverity.CRITICAL) = Notification.OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = null,
        monitorType = Monitor.Type.CPU_LOAD,
        startTime = now,
        threshold = MonitoredValue.FractionalValue(0.9f),
        value = MonitoredValue.FractionalValue(0.95f),
        inertia = Duration.ZERO,
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
                threshold = MonitoredValue.FractionalValue(0.9f),
                startValue = MonitoredValue.FractionalValue(0.95f),
                endValue = MonitoredValue.FractionalValue(0.2f)
            )
        )

        // Then
        sent.captured.severity shouldBe null
        sent.captured.priority shouldBe 3
    }

    @Test
    fun `a snoozed manager calls no service`() {
        // Given
        snoozeNotificationsService.snooze(now.plus(Duration.ofHours(1)), null)

        // When
        manager.notify(ongoingEvent())

        // Then
        verify(exactly = 0) { ntfyService.notify(any()) }
        verify(exactly = 0) { webhookService.notify(any()) }
        verify(exactly = 0) { mqttNotificationService.notify(any()) }
    }

    @Test
    fun `an expired snooze resumes delivery without an explicit resumeNotifications`() {
        // Given
        snoozeNotificationsService.snooze(now.plus(Duration.ofHours(1)), null)
        now = now.plus(Duration.ofHours(1)).plusSeconds(1)

        // When
        manager.notify(ongoingEvent())

        // Then
        verify(exactly = 1) { ntfyService.notify(any()) }
    }

    @Test
    fun `the suppressed counter increments once per dropped notification`() {
        // Given
        snoozeNotificationsService.snooze(now.plus(Duration.ofHours(1)), null)

        // When
        manager.notify(ongoingEvent())
        manager.notify(ongoingEvent())

        // Then
        snoozeNotificationsService.info().suppressedNotificationCount shouldBe 2
    }
}
