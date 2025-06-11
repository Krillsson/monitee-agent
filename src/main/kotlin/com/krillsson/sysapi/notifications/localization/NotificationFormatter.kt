package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.notifications.Notification
import org.springframework.stereotype.Component

@Component
class NotificationFormatter(
    private val genericEventFormatter: GenericEventFormatter,
    private val ongoingEventFormatter: OngoingEventFormatter
) {
    fun formatNotification(notification: Notification): Pair<String, String> {
        return when (notification) {
            is Notification.OngoingEvent -> ongoingEventFormatter.formatOngoingEventTitle(
                notification
            ) to ongoingEventFormatter.formatOngoingEventDescription(
                notification
            )

            is Notification.GenericEvent.MonitoredItemMissing -> genericEventFormatter.formatMonitoredItemMissingTitle(
                notification
            ) to genericEventFormatter.formatMonitoredItemMissingDescription(notification)

            is Notification.GenericEvent.UpdateAvailable -> genericEventFormatter.formatUpdateEventTitle() to genericEventFormatter.formatUpdateEventDescription(
                notification
            )
        }
    }
}