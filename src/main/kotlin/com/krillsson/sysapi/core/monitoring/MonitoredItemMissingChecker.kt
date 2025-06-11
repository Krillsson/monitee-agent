package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.core.genericevents.MonitoredItemMissing
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.notifications.NotificationManager
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class MonitoredItemMissingChecker(
    private val repository: GenericEventRepository,
    private val notificationManager: NotificationManager
) {
    private val logger by logger()

    fun reportItemMissing(monitor: Monitor<*>) {
        val existingEvent = getExistingEventForMonitor(monitor.id)
        if (existingEvent == null) {
            logger.info("Creating event about missing item for monitor ${monitor.type.name} / ${monitor.config.monitoredItemId} (${monitor.id})")
            val event = MonitoredItemMissing(
                UUID.randomUUID(),
                Instant.now(),
                monitor.type,
                monitor.id,
                monitor.config.monitoredItemId
            )
            repository.add(
                event
            )
            notificationManager.notify(event.asNotification())
        } else {
            logger.debug("Event about missing item already exists for monitor ${monitor.type.name} / ${monitor.config.monitoredItemId} (${monitor.id})")
        }
    }

    fun removeItemMissingEvent(monitor: Monitor<*>) {
        val existingEvent = getExistingEventForMonitor(monitor.id)
        if (existingEvent != null) {
            logger.info("Removing event since item re-appeared ${monitor.type.name} / ${monitor.config.monitoredItemId} (${monitor.id})")
            repository.removeById(existingEvent.id)
        }
    }

    private fun getExistingEventForMonitor(monitorId: UUID) = repository.read()
        .firstOrNull { event -> (event as? MonitoredItemMissing)?.monitorId == monitorId }
}

private fun MonitoredItemMissing.asNotification(): Notification {
    return Notification.GenericEvent.MonitoredItemMissing(
        id = id,
        timestamp = timestamp,
        monitorType = monitorType,
        monitorId = monitorId,
        monitoredItemId = monitoredItemId
    )
}
