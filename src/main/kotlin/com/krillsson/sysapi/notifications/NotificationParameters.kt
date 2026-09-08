package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.Instant

data class NotificationParameters(
    val title: String,
    val message: String,
    val clickUrl: String,
    val priority: Int,
    val eventType: NotificationEventType,
    val monitorType: Monitor.Type?,
    val severity: EventSeverity?,
    val timestamp: Instant,
    val serverName: String,
    val serverId: String,
    val actions: List<NotificationAction> = emptyList()
)
