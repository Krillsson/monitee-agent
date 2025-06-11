package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.notifications.localization.NotificationFormatter
import com.krillsson.sysapi.notifications.ntfy.NtfyService
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service

@Service
class NotificationManager(
    private val serverIdService: ServerIdService,
    private val ntfyService: NtfyService,
    private val notificationFormatter: NotificationFormatter,
    private val deeplinkCreator: DeeplinkCreator
) {
    private val logger by logger()

    private val notificationServices = listOf<NotificationService>(
        ntfyService
    )

    fun notificationServiceInfo(): NotificationServiceInfo {
        return NotificationServiceInfo(
            serverId = serverIdService.serverId.toString(),
            ntfy = ntfyService.ntfyInfo()
        )
    }

    fun notify(notification: Notification) {
        for (service in notificationServices) {
            logger.info("Sending {} to {}", notification, service::class.simpleName)
            service.notify(notification.asNotificationParameters())
        }
    }

    private fun Notification.asNotificationParameters(): NotificationParameters {
        val (title, message) = notificationFormatter.formatNotification(this)
        return NotificationParameters(
            title = title,
            message = message,
            clickUrl = deeplinkCreator.createDeeplink(this),
            if (this is Notification.OngoingEvent) 4 else 3
        )
    }
}
