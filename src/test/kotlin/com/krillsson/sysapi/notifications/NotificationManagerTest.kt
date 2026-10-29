package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.NotificationsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.core.monitoring.event.EventRepository
import com.krillsson.sysapi.mqtt.MqttNotificationService
import com.krillsson.sysapi.notifications.localization.NotificationFormatter
import com.krillsson.sysapi.notifications.ntfy.NtfyService
import com.krillsson.sysapi.notifications.webhook.WebhookService
import com.krillsson.sysapi.serverid.ServerIdService
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationManagerTest {

    private val store = mutableMapOf<String, String>()
    private var now = Instant.parse("2026-09-08T12:00:00Z")
    private lateinit var clock: Clock
    private lateinit var snoozeNotificationsService: SnoozeNotificationsService
    private lateinit var notificationPolicyService: NotificationPolicyService
    private lateinit var eventManager: EventManager

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
        every { acknowledge(any()) } answers { "https://monitee.app/server/id/event/${firstArg<UUID>()}/acknowledge" }
    }
    private val serverIdService: ServerIdService = mockk {
        every { serverId } returns UUID.fromString("11111111-2222-3333-4444-555555555555")
    }
    private val configFile: YAMLConfigFile = mockk {
        every { notifications } returns NotificationsConfiguration(serverName = "test-server")
        every { metricsConfig } returns MetricsConfiguration()
    }

    private lateinit var manager: NotificationManager

    @BeforeEach
    fun setUp() {
        every { ntfyService.notify(capture(sent)) } returns Unit

        store.clear()
        val repository = inMemoryKeyValueRepository(store)

        clock = mockk()
        every { clock.instant() } answers { now }
        every { clock.zone } returns ZoneOffset.UTC

        snoozeNotificationsService = SnoozeNotificationsService(repository, clock)
        notificationPolicyService = NotificationPolicyService(inMemoryNotificationPolicyStore(), clock)

        val eventRepository = mockk<EventRepository> {
            every { read() } returns emptyList()
            every { write(any()) } just Runs
        }
        eventManager = EventManager(eventRepository, clock, configFile).also { it.start() }

        manager = NotificationManager(
            serverIdService,
            ntfyService,
            webhookService,
            mqttNotificationService,
            notificationFormatter,
            configFile,
            deeplinkCreator,
            snoozeNotificationsService,
            notificationPolicyService,
            eventManager,
            clock
        )
    }

    private fun ongoingEvent(
        severity: EventSeverity = EventSeverity.CRITICAL,
        id: UUID = UUID.randomUUID(),
        monitorId: UUID = UUID.randomUUID()
    ) = Notification.OngoingEvent(
        id = id,
        monitorId = monitorId,
        monitoredItemId = null,
        monitorType = Monitor.Type.CPU_LOAD,
        startTime = now,
        threshold = MonitoredValue.FractionalValue(0.9f),
        warningThreshold = null,
        value = MonitoredValue.FractionalValue(0.95f),
        inertia = Duration.ZERO,
        severity = severity
    )

    private fun resolvedEvent(raise: Notification.OngoingEvent = ongoingEvent()) = Notification.ResolvedEvent(
        id = raise.id,
        monitorId = raise.monitorId,
        monitoredItemId = null,
        monitorType = Monitor.Type.CPU_LOAD,
        startTime = Instant.parse("2026-08-01T09:00:00Z"),
        endTime = Instant.parse("2026-08-01T10:00:00Z"),
        threshold = MonitoredValue.FractionalValue(0.9f),
        startValue = MonitoredValue.FractionalValue(0.95f),
        endValue = MonitoredValue.FractionalValue(0.2f)
    )

    private fun recordInEventManager(notification: Notification.OngoingEvent) {
        eventManager.add(
            OngoingEvent(
                id = notification.id,
                monitorId = notification.monitorId,
                monitoredItemId = notification.monitoredItemId,
                monitorType = notification.monitorType,
                startTime = notification.startTime,
                threshold = notification.threshold,
                value = notification.value,
                severity = notification.severity
            )
        )
    }

    private fun enableQuietHours(
        start: String = "22:00",
        end: String = "07:00",
        days: List<DayOfWeek>? = null,
        timeZone: String = "UTC",
        allowCritical: Boolean? = null
    ) {
        notificationPolicyService.update(
            NotificationPolicyUpdate(
                quietHours = QuietHoursUpdate(
                    enabled = true,
                    start = start,
                    end = end,
                    days = days,
                    timeZone = timeZone,
                    allowCritical = allowCritical
                )
            )
        )
    }

    private fun sendsNothing() {
        verify(exactly = 0) { ntfyService.notify(any()) }
        verify(exactly = 0) { webhookService.notify(any()) }
        verify(exactly = 0) { mqttNotificationService.notify(any()) }
    }

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
        manager.notify(resolvedEvent())

        // Then
        sent.captured.severity shouldBe null
        sent.captured.priority shouldBe 3
    }

    @Test
    fun `passes the event id on to the services`() {
        // Given
        val raise = ongoingEvent()

        // When
        manager.notify(raise)

        // Then
        sent.captured.eventId shouldBe raise.id.toString()
    }

    @Test
    fun `a snoozed manager calls no service`() {
        // Given
        snoozeNotificationsService.snooze(now.plus(Duration.ofHours(1)), null)

        // When
        val notified = manager.notify(ongoingEvent())

        // Then
        notified shouldBe false
        sendsNothing()
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

    @Test
    fun `an ongoing event notification carries a Snooze action followed by an Acknowledge action`() {
        // Given
        val raise = ongoingEvent()

        // When
        manager.notify(raise)

        // Then
        sent.captured.actions shouldBe listOf(
            NotificationAction.View(label = "Snooze", url = "https://monitee.app/server/id/snooze", clear = true),
            NotificationAction.View(
                label = "Acknowledge",
                url = "https://monitee.app/server/id/event/${raise.id}/acknowledge",
                clear = true
            )
        )
    }

    @Test
    fun `drops a notification inside quiet hours`() {
        // Given
        enableQuietHours()
        now = Instant.parse("2026-09-08T23:00:00Z")

        // When
        val notified = manager.notify(ongoingEvent())

        // Then
        notified shouldBe false
        sendsNothing()
    }

    @Test
    fun `sends a notification outside quiet hours`() {
        // Given
        enableQuietHours()

        // When
        val notified = manager.notify(ongoingEvent())

        // Then
        notified shouldBe true
        verify(exactly = 1) { ntfyService.notify(any()) }
    }

    @ParameterizedTest
    @CsvSource(
        "2026-09-08T21:59:00Z, true",
        "2026-09-08T22:00:00Z, false",
        "2026-09-09T03:00:00Z, false",
        "2026-09-09T06:59:00Z, false",
        "2026-09-09T07:00:00Z, true"
    )
    fun `honours a quiet hours window that crosses midnight`(at: String, expectedSent: Boolean) {
        // Given
        enableQuietHours(start = "22:00", end = "07:00")
        now = Instant.parse(at)

        // When
        val notified = manager.notify(ongoingEvent())

        // Then
        notified shouldBe expectedSent
    }

    @ParameterizedTest
    @CsvSource(
        "2026-09-11T23:00:00Z, false",
        "2026-09-12T03:00:00Z, false",
        "2026-09-12T23:00:00Z, true",
        "2026-09-11T03:00:00Z, true"
    )
    fun `opens quiet hours only on the chosen days, keeping the morning after with the day it opened`(
        at: String,
        expectedSent: Boolean
    ) {
        // Given
        enableQuietHours(days = listOf(DayOfWeek.FRIDAY))
        now = Instant.parse(at)

        // When
        val notified = manager.notify(ongoingEvent())

        // Then
        notified shouldBe expectedSent
    }

    @Test
    fun `lets a critical event through quiet hours when allowCritical is on, but not a warning`() {
        // Given
        enableQuietHours(allowCritical = true)
        now = Instant.parse("2026-09-08T23:00:00Z")

        // When
        val critical = manager.notify(ongoingEvent(EventSeverity.CRITICAL))
        val warning = manager.notify(ongoingEvent(EventSeverity.WARNING))

        // Then
        critical shouldBe true
        warning shouldBe false
    }

    @Test
    fun `reads quiet hours in the configured time zone`() {
        // Given
        now = Instant.parse("2026-09-08T21:30:00Z")

        // When
        enableQuietHours(timeZone = "Europe/Stockholm")
        val inStockholm = manager.notify(ongoingEvent())
        enableQuietHours(timeZone = "UTC")
        val inUtc = manager.notify(ongoingEvent())

        // Then
        inStockholm shouldBe false
        inUtc shouldBe true
    }

    @Test
    fun `drops quiet hours for notifications that do not come from a monitor too`() {
        // Given
        enableQuietHours()
        now = Instant.parse("2026-09-08T23:00:00Z")

        // When
        val notified = manager.notify(Notification.GenericEvent.ContainerImageUpdateDigest(now, listOf("nginx")))

        // Then
        notified shouldBe false
    }

    @Test
    fun `drops a second raise for the same monitor inside the cooldown`() {
        // Given
        val monitorId = UUID.randomUUID()
        manager.notify(ongoingEvent(monitorId = monitorId))
        now = now.plus(Duration.ofMinutes(4))

        // When
        val notified = manager.notify(ongoingEvent(monitorId = monitorId))

        // Then
        notified shouldBe false
        verify(exactly = 1) { ntfyService.notify(any()) }
    }

    @Test
    fun `sends a raise for a different monitor inside the cooldown`() {
        // Given
        manager.notify(ongoingEvent())
        now = now.plus(Duration.ofMinutes(1))

        // When
        val notified = manager.notify(ongoingEvent())

        // Then
        notified shouldBe true
    }

    @Test
    fun `sends a raise for the same monitor once the cooldown has passed`() {
        // Given
        val monitorId = UUID.randomUUID()
        manager.notify(ongoingEvent(monitorId = monitorId))
        now = now.plus(Duration.ofMinutes(5))

        // When
        val notified = manager.notify(ongoingEvent(monitorId = monitorId))

        // Then
        notified shouldBe true
    }

    @Test
    fun `sends every raise when the cooldown is zero`() {
        // Given
        notificationPolicyService.update(NotificationPolicyUpdate(cooldownMinutes = 0))
        val monitorId = UUID.randomUUID()
        manager.notify(ongoingEvent(monitorId = monitorId))

        // When
        val notified = manager.notify(ongoingEvent(monitorId = monitorId))

        // Then
        notified shouldBe true
    }

    @Test
    fun `sends the escalation of the event that started the cooldown`() {
        // Given
        val raise = ongoingEvent(EventSeverity.WARNING)
        manager.notify(raise)
        now = now.plus(Duration.ofMinutes(1))

        // When
        val notified = manager.notify(raise.copy(severity = EventSeverity.CRITICAL))

        // Then
        notified shouldBe true
    }

    @Test
    fun `drops the resolve of a raise that the cooldown dropped`() {
        // Given
        val monitorId = UUID.randomUUID()
        manager.notify(ongoingEvent(monitorId = monitorId))
        val droppedRaise = ongoingEvent(monitorId = monitorId)
        manager.notify(droppedRaise)
        clearMocks(ntfyService, answers = false)

        // When
        val notified = manager.notify(resolvedEvent(droppedRaise))

        // Then
        notified shouldBe false
        verify(exactly = 0) { ntfyService.notify(any()) }
    }

    @Test
    fun `sends the resolve of a raise that went out`() {
        // Given
        val raise = ongoingEvent()
        manager.notify(raise)

        // When
        val notified = manager.notify(resolvedEvent(raise))

        // Then
        notified shouldBe true
    }

    @Test
    fun `drops only resolves when notifyOnRecovery is off`() {
        // Given
        notificationPolicyService.update(NotificationPolicyUpdate(notifyOnRecovery = false))

        // When
        val raise = manager.notify(ongoingEvent())
        val resolve = manager.notify(resolvedEvent())

        // Then
        raise shouldBe true
        resolve shouldBe false
    }

    @Test
    fun `drops another raise notification for an acknowledged event`() {
        // Given
        val raise = ongoingEvent(EventSeverity.CRITICAL)
        recordInEventManager(raise)
        eventManager.acknowledge(raise.id, "on it")

        // When
        val notified = manager.notify(raise)

        // Then
        notified shouldBe false
        sendsNothing()
    }

    @Test
    fun `sends the escalation of an acknowledged warning and clears the acknowledgement`() {
        // Given
        val raise = ongoingEvent(EventSeverity.WARNING)
        recordInEventManager(raise)
        eventManager.acknowledge(raise.id, "on it")

        // When
        val notified = manager.notify(raise.copy(severity = EventSeverity.CRITICAL))

        // Then
        notified shouldBe true
        eventManager.ongoingEventById(raise.id).shouldNotBeNull().acknowledgedAt shouldBe null
    }

    @Test
    fun `sends the resolve of an acknowledged event`() {
        // Given
        val raise = ongoingEvent()
        manager.notify(raise)
        recordInEventManager(raise)
        eventManager.acknowledge(raise.id, null)

        // When
        val notified = manager.notify(resolvedEvent(raise))

        // Then
        notified shouldBe true
    }

    @Test
    fun `counts dropped notifications per reason`() {
        // Given
        val acknowledged = ongoingEvent()
        recordInEventManager(acknowledged)
        eventManager.acknowledge(acknowledged.id, null)
        val monitorId = UUID.randomUUID()
        notificationPolicyService.update(NotificationPolicyUpdate(notifyOnRecovery = false))

        // When
        manager.notify(acknowledged)
        manager.notify(ongoingEvent(monitorId = monitorId))
        manager.notify(ongoingEvent(monitorId = monitorId))
        manager.notify(resolvedEvent())
        manager.notify(resolvedEvent())
        enableQuietHours()
        now = Instant.parse("2026-09-08T23:00:00Z")
        manager.notify(ongoingEvent())

        // Then
        notificationPolicyService.policy().suppressed shouldBe SuppressedNotificationCounts(
            acknowledged = 1,
            cooldown = 1,
            quietHours = 1,
            recovery = 2
        )
    }

    @Test
    fun `sends one summary of the unacknowledged events raised during quiet hours when they end`() {
        // Given
        enableQuietHours()
        now = Instant.parse("2026-09-08T23:00:00Z")
        manager.summarizeWhenQuietHoursEnd()
        val unacknowledged = ongoingEvent()
        val acknowledged = ongoingEvent()
        listOf(unacknowledged, acknowledged).forEach {
            recordInEventManager(it)
            manager.notify(it)
        }
        eventManager.acknowledge(acknowledged.id, null)

        // When
        now = Instant.parse("2026-09-09T07:00:00Z")
        manager.summarizeWhenQuietHoursEnd()

        // Then
        verify(exactly = 1) { ntfyService.notify(any()) }
        sent.captured.eventType shouldBe NotificationEventType.QUIET_HOURS_SUMMARY
        sent.captured.eventId shouldBe null
        verify {
            notificationFormatter.formatNotification(match { notification ->
                notification is Notification.GenericEvent.QuietHoursSummary &&
                        notification.events.map { it.id } == listOf(unacknowledged.id)
            }, "test-server")
        }
    }

    @Test
    fun `sends no summary when nothing is outstanding as quiet hours end`() {
        // Given
        enableQuietHours()
        now = Instant.parse("2026-09-08T23:00:00Z")
        manager.summarizeWhenQuietHoursEnd()
        val resolvedInside = ongoingEvent()
        manager.notify(resolvedInside)
        manager.notify(resolvedEvent(resolvedInside))

        // When
        now = Instant.parse("2026-09-09T07:00:00Z")
        manager.summarizeWhenQuietHoursEnd()

        // Then
        sendsNothing()
    }

    @Test
    fun `sends the summary only once`() {
        // Given
        enableQuietHours()
        now = Instant.parse("2026-09-08T23:00:00Z")
        manager.summarizeWhenQuietHoursEnd()
        val raise = ongoingEvent()
        recordInEventManager(raise)
        manager.notify(raise)
        now = Instant.parse("2026-09-09T07:00:00Z")
        manager.summarizeWhenQuietHoursEnd()

        // When
        now = Instant.parse("2026-09-09T07:01:00Z")
        manager.summarizeWhenQuietHoursEnd()

        // Then
        verify(exactly = 1) { ntfyService.notify(any()) }
    }

    @Test
    fun `sends the resolve of an event that was in the quiet hours summary`() {
        // Given
        enableQuietHours()
        now = Instant.parse("2026-09-08T23:00:00Z")
        manager.summarizeWhenQuietHoursEnd()
        val raise = ongoingEvent()
        recordInEventManager(raise)
        manager.notify(raise)
        now = Instant.parse("2026-09-09T07:00:00Z")
        manager.summarizeWhenQuietHoursEnd()

        // When
        val notified = manager.notify(resolvedEvent(raise))

        // Then
        notified shouldBe true
    }
}
