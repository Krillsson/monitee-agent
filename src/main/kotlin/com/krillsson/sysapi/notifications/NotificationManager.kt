package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.notifications.localization.NotificationFormatter
import com.krillsson.sysapi.notifications.ntfy.NtfyService
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.util.EnvironmentUtils
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service

@Service
class NotificationManager(
    private val serverIdService: ServerIdService,
    private val ntfyService: NtfyService,
    private val notificationFormatter: NotificationFormatter,
    private val configFile: YAMLConfigFile,
    private val deeplinkCreator: DeeplinkCreator
) {
    private val logger by logger()

    private val config = configFile.notifications

    private val serverName = config.serverName ?: EnvironmentUtils.hostName

    private val notificationServices = listOf<NotificationService>(
        ntfyService
    )

    fun notificationServiceInfo(): NotificationServiceInfo {
        return NotificationServiceInfo(
            serverName = serverName,
            serverId = serverIdService.serverId.toString(),
            ntfy = ntfyService.ntfyInfo()
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
            if (this is Notification.OngoingEvent) 4 else 3
        )
    }
}
