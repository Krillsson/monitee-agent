package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.notifications.Notification
import org.springframework.stereotype.Component

@Component
class NotificationFormatter(
    private val genericEventFormatter: GenericEventFormatter,
    private val ongoingEventFormatter: OngoingEventFormatter
) {
    fun formatNotification(notification: Notification, serverName: String): Pair<String, String> {
        return when (notification) {
            is Notification.OngoingEvent -> ongoingEventFormatter.formatOngoingEventTitle(
                notification, serverName
            ) to ongoingEventFormatter.formatOngoingEventDescription(
                notification
            )

            is Notification.GenericEvent.MonitoredItemMissing -> genericEventFormatter.formatMonitoredItemMissingTitle(
                notification, serverName
            ) to genericEventFormatter.formatMonitoredItemMissingDescription(notification)

            is Notification.GenericEvent.UpdateAvailable -> genericEventFormatter.formatUpdateEventTitle(serverName) to genericEventFormatter.formatUpdateEventDescription(
                notification
            )
        }
    }
}