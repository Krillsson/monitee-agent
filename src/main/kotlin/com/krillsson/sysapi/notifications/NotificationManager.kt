package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.mqtt.MqttNotificationService
import com.krillsson.sysapi.notifications.localization.NotificationFormatter
import com.krillsson.sysapi.notifications.ntfy.NtfyService
import com.krillsson.sysapi.notifications.webhook.WebhookService
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.util.EnvironmentUtils
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
class NotificationManager(
    private val serverIdService: ServerIdService,
    private val ntfyService: NtfyService,
    private val webhookService: WebhookService,
    private val mqttNotificationService: MqttNotificationService,
    private val notificationFormatter: NotificationFormatter,
    private val configFile: YAMLConfigFile,
    private val deeplinkCreator: DeeplinkCreator,
    private val snoozeNotificationsService: SnoozeNotificationsService,
    private val notificationPolicyService: NotificationPolicyService,
    private val eventManager: EventManager,
    private val clock: Clock
) {
    private val logger by logger()

    private val config = configFile.notifications

    private val serverName = config.serverName ?: EnvironmentUtils.hostName

    private val notificationServices = listOf<NotificationService>(
        ntfyService,
        webhookService,
        mqttNotificationService
    )

    private val lastRaiseByMonitor = ConcurrentHashMap<UUID, SentRaise>()

    private val withheldRaises = ConcurrentHashMap<UUID, WithheldRaise>()

    @Volatile
    private var quietHoursWereActive = false

    fun notificationServiceInfo(): NotificationServiceInfo {
        return NotificationServiceInfo(
            serverName = serverName,
            serverId = serverIdService.serverId.toString(),
            ntfy = ntfyService.ntfyInfo(),
            webhooks = webhookService.webhookInfo(),
            mqtt = mqttNotificationService.mqttInfo(),
            snooze = snoozeNotificationsService.info(),
            policy = notificationPolicyService.policy()
        )
    }

    fun notify(notification: Notification): Boolean {
        val snoozed = snoozeNotificationsService.isSnoozed()
        val suppression = if (snoozed) null else suppressionFor(notification)
        when {
            snoozed -> dropSnoozed(notification)
            suppression != null -> dropSuppressed(notification, suppression)
            else -> send(notification)
        }
        return !snoozed && suppression == null
    }

    private fun dropSnoozed(notification: Notification) {
        snoozeNotificationsService.recordSuppressed()
        logger.info(
            "Dropping {} - notifications snoozed until {}",
            notification,
            snoozeNotificationsService.info().snoozedUntil
        )
    }

    private fun dropSuppressed(notification: Notification, suppression: SuppressionReason) {
        notificationPolicyService.recordSuppressed(suppression)
        logger.info("Dropping {} - {}", notification, suppression.description)
    }

    private fun send(notification: Notification) {
        if (notification is Notification.OngoingEvent) {
            lastRaiseByMonitor[notification.monitorId] = SentRaise(notification.id, clock.instant())
            withheldRaises.remove(notification.id)
        }
        notificationServices.filter { it.enabled }.forEach { service ->
            logger.info("Sending {} to {}", notification, service::class.simpleName)
            service.notify(notification.asNotificationParameters())
        }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    fun summarizeWhenQuietHoursEnd() {
        val active = notificationPolicyService.isQuietHoursActive()
        val ended = quietHoursWereActive && !active
        quietHoursWereActive = active
        if (ended) {
            sendQuietHoursSummary()
        }
    }

    private fun sendQuietHoursSummary() {
        val withheldInQuietHours = withheldRaises.filterValues { it.reason == SuppressionReason.QUIET_HOURS }
        withheldInQuietHours.keys.forEach { withheldRaises.remove(it) }
        val outstanding = withheldInQuietHours.values
            .map { it.notification }
            .filter { notification ->
                eventManager.ongoingEventById(notification.id)?.let { it.acknowledgedAt == null } ?: false
            }
            .sortedBy { it.startTime }
        when {
            outstanding.isEmpty() -> logger.info("Quiet hours ended with nothing outstanding")
            else -> notify(Notification.GenericEvent.QuietHoursSummary(clock.instant(), outstanding))
        }
    }

    private fun suppressionFor(notification: Notification): SuppressionReason? {
        val settings = notificationPolicyService.settings()
        val quietHoursActive = notificationPolicyService.isQuietHoursActive()
        return when (notification) {
            is Notification.OngoingEvent -> acknowledgementSuppression(notification)
                ?: notification.suppressedBy(
                    SuppressionReason.COOLDOWN,
                    notification.isInsideCooldown(settings.cooldownMinutes)
                )
                ?: notification.suppressedBy(
                    SuppressionReason.QUIET_HOURS,
                    quietHoursActive && !notification.bypassesQuietHours(settings.quietHours)
                )

            is Notification.ResolvedEvent -> withheldRaises.remove(notification.id)?.reason
                ?: SuppressionReason.QUIET_HOURS.takeIf { quietHoursActive }
                ?: SuppressionReason.RECOVERY.takeIf { !settings.notifyOnRecovery }

            else -> SuppressionReason.QUIET_HOURS.takeIf { quietHoursActive }
        }
    }

    private fun acknowledgementSuppression(notification: Notification.OngoingEvent): SuppressionReason? {
        val event = eventManager.ongoingEventById(notification.id)
        return when {
            event?.acknowledgedAt == null -> null
            event.isEscalatedBy(notification) -> {
                clearAcknowledgement(notification)
                null
            }

            else -> SuppressionReason.ACKNOWLEDGED
        }
    }

    private fun OngoingEvent.isEscalatedBy(notification: Notification.OngoingEvent) =
        severity == EventSeverity.WARNING && notification.severity == EventSeverity.CRITICAL

    private fun clearAcknowledgement(notification: Notification.OngoingEvent) {
        logger.info("Event {} escalated to {}, clearing its acknowledgement", notification.id, notification.severity)
        eventManager.unacknowledge(notification.id)
    }

    private fun Notification.OngoingEvent.suppressedBy(reason: SuppressionReason, suppressed: Boolean): SuppressionReason? =
        when {
            suppressed -> reason.also { withheldRaises[id] = WithheldRaise(it, this) }
            else -> null
        }

    private fun Notification.OngoingEvent.isInsideCooldown(cooldownMinutes: Int): Boolean {
        val last = lastRaiseByMonitor[monitorId]
        return when {
            cooldownMinutes == 0 || last == null -> false
            else -> last.eventId != id && clock.instant().isBefore(last.at.plus(Duration.ofMinutes(cooldownMinutes.toLong())))
        }
    }

    private fun Notification.OngoingEvent.bypassesQuietHours(quietHours: QuietHoursSettings) =
        quietHours.allowCritical && severity == EventSeverity.CRITICAL

    private fun Notification.asNotificationParameters(): NotificationParameters {
        val (title, message) = notificationFormatter.formatNotification(this, serverName)
        return NotificationParameters(
            title = title,
            message = message,
            clickUrl = deeplinkCreator.createDeeplink(this),
            priority = priority(),
            eventType = eventType(),
            monitorType = monitorType(),
            severity = severity(),
            timestamp = timestamp(),
            serverName = serverName,
            serverId = serverIdService.serverId.toString(),
            eventId = eventId()?.toString(),
            actions = actions()
        )
    }

    private fun Notification.priority() = when {
        this !is Notification.OngoingEvent -> 3
        severity == EventSeverity.WARNING -> 3
        else -> 4
    }

    private fun Notification.severity() = when (this) {
        is Notification.OngoingEvent -> severity
        else -> null
    }

    private fun Notification.actions(): List<NotificationAction> = when (this) {
        is Notification.OngoingEvent -> listOf(
            NotificationAction.View(label = "Snooze", url = deeplinkCreator.snooze(), clear = true),
            NotificationAction.View(label = "Acknowledge", url = deeplinkCreator.acknowledge(id), clear = true)
        )

        else -> emptyList()
    }

    private fun Notification.eventType() = when (this) {
        is Notification.OngoingEvent -> NotificationEventType.ONGOING_EVENT
        is Notification.ResolvedEvent -> NotificationEventType.RESOLVED_EVENT
        is Notification.GenericEvent.UpdateAvailable -> NotificationEventType.UPDATE_AVAILABLE
        is Notification.GenericEvent.MonitoredItemMissing -> NotificationEventType.MONITORED_ITEM_MISSING
        is Notification.GenericEvent.ContainerImageUpdateAvailable -> NotificationEventType.CONTAINER_IMAGE_UPDATE_AVAILABLE
        is Notification.GenericEvent.ContainerImageUpdateDigest -> NotificationEventType.CONTAINER_IMAGE_UPDATE_DIGEST
        is Notification.GenericEvent.QuietHoursSummary -> NotificationEventType.QUIET_HOURS_SUMMARY
    }

    private fun Notification.monitorType() = when (this) {
        is Notification.OngoingEvent -> monitorType
        is Notification.ResolvedEvent -> monitorType
        is Notification.GenericEvent.MonitoredItemMissing -> monitorType
        else -> null
    }

    private fun Notification.eventId(): UUID? = when (this) {
        is Notification.OngoingEvent -> id
        is Notification.ResolvedEvent -> id
        is Notification.GenericEvent.UpdateAvailable -> id
        is Notification.GenericEvent.MonitoredItemMissing -> id
        is Notification.GenericEvent.ContainerImageUpdateAvailable -> id
        is Notification.GenericEvent.ContainerImageUpdateDigest -> null
        is Notification.GenericEvent.QuietHoursSummary -> null
    }

    private fun Notification.timestamp(): Instant = when (this) {
        is Notification.OngoingEvent -> startTime
        is Notification.ResolvedEvent -> endTime
        is Notification.GenericEvent.UpdateAvailable -> timestamp
        is Notification.GenericEvent.MonitoredItemMissing -> timestamp
        is Notification.GenericEvent.ContainerImageUpdateAvailable -> timestamp
        is Notification.GenericEvent.ContainerImageUpdateDigest -> timestamp
        is Notification.GenericEvent.QuietHoursSummary -> timestamp
    }

    private data class SentRaise(val eventId: UUID, val at: Instant)

    private data class WithheldRaise(val reason: SuppressionReason, val notification: Notification.OngoingEvent)
}
