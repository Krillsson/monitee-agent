package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.YAMLConfigFile
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
    private val notificationFormatter: NotificationFormatter,
    private val configFile: YAMLConfigFile,
    private val deeplinkCreator: DeeplinkCreator
) {
    private val logger by logger()

    private val config = configFile.notifications

    private val serverName = config.serverName ?: EnvironmentUtils.hostName

    private val notificationServices = listOf<NotificationService>(
        ntfyService,
        webhookService
    )

    fun notificationServiceInfo(): NotificationServiceInfo {
        return NotificationServiceInfo(
            serverName = serverName,
            serverId = serverIdService.serverId.toString(),
            ntfy = ntfyService.ntfyInfo(),
            webhooks = webhookService.webhookInfo()
        )
    }

    fun notify(notification: Notification) {
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
            priority = if (this is Notification.OngoingEvent) 4 else 3,
            eventType = eventType(),
            monitorType = monitorType()?.name,
            timestamp = timestamp(),
            serverName = serverName,
            serverId = serverIdService.serverId.toString()
        )
    }

    private fun Notification.eventType() = when (this) {
        is Notification.OngoingEvent -> "ONGOING_EVENT"
        is Notification.ResolvedEvent -> "RESOLVED_EVENT"
        is Notification.GenericEvent.UpdateAvailable -> "UPDATE_AVAILABLE"
        is Notification.GenericEvent.MonitoredItemMissing -> "MONITORED_ITEM_MISSING"
        is Notification.GenericEvent.ContainerImageUpdateAvailable -> "CONTAINER_IMAGE_UPDATE_AVAILABLE"
        is Notification.GenericEvent.ContainerImageUpdateDigest -> "CONTAINER_IMAGE_UPDATE_DIGEST"
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
