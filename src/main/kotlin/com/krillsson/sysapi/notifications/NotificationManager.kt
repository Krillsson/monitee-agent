package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.mqtt.MqttNotificationService
import com.krillsson.sysapi.notifications.localization.NotificationFormatter
import com.krillsson.sysapi.notifications.ntfy.NtfyService
import com.krillsson.sysapi.notifications.webhook.WebhookService
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.util.EnvironmentUtils
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NotificationManager(
    private val serverIdService: ServerIdService,
    private val ntfyService: NtfyService,
    private val webhookService: WebhookService,
    private val mqttNotificationService: MqttNotificationService,
    private val notificationFormatter: NotificationFormatter,
    private val configFile: YAMLConfigFile,
    private val deeplinkCreator: DeeplinkCreator,
    private val snoozeNotificationsService: SnoozeNotificationsService
) {
    private val logger by logger()

    private val config = configFile.notifications

    private val serverName = config.serverName ?: EnvironmentUtils.hostName

    private val notificationServices = listOf<NotificationService>(
        ntfyService,
        webhookService,
        mqttNotificationService
    )

    fun notificationServiceInfo(): NotificationServiceInfo {
        return NotificationServiceInfo(
            serverName = serverName,
            serverId = serverIdService.serverId.toString(),
            ntfy = ntfyService.ntfyInfo(),
            webhooks = webhookService.webhookInfo(),
            mqtt = mqttNotificationService.mqttInfo(),
            snooze = snoozeNotificationsService.info()
        )
    }

    fun notify(notification: Notification) {
        if (snoozeNotificationsService.isSnoozed()) {
            snoozeNotificationsService.recordSuppressed()
            logger.info(
                "Dropping {} - notifications snoozed until {}",
                notification,
                snoozeNotificationsService.info().snoozedUntil
            )
            return
        }
        for (service in notificationServices) {
            if (service.enabled) {
                logger.info("Sending {} to {}", notification, service::class.simpleName)
                service.notify(notification.asNotificationParameters())
            }
        }
    }

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
            NotificationAction.View(label = "Snooze", url = deeplinkCreator.snooze(), clear = true)
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
    }

    private fun Notification.monitorType() = when (this) {
        is Notification.OngoingEvent -> monitorType
        is Notification.ResolvedEvent -> monitorType
        is Notification.GenericEvent.MonitoredItemMissing -> monitorType
        else -> null
    }

    private fun Notification.timestamp(): Instant = when (this) {
        is Notification.OngoingEvent -> startTime
        is Notification.ResolvedEvent -> endTime
        is Notification.GenericEvent.UpdateAvailable -> timestamp
        is Notification.GenericEvent.MonitoredItemMissing -> timestamp
        is Notification.GenericEvent.ContainerImageUpdateAvailable -> timestamp
        is Notification.GenericEvent.ContainerImageUpdateDigest -> timestamp
    }
}
