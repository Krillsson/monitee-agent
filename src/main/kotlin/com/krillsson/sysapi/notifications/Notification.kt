package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.Duration
import java.time.Instant
import java.util.*

sealed interface Notification {
    data class OngoingEvent(
        val id: UUID,
        val monitorId: UUID,
        val monitoredItemId: String?,
        val monitorType: Monitor.Type,
        val startTime: Instant,
        val threshold: MonitoredValue,
        val value: MonitoredValue,
        val inertia: Duration
    ) : Notification

    sealed interface GenericEvent : Notification {
        class UpdateAvailable(
            val id: UUID,
            val timestamp: Instant,
            val currentVersion: String,
            val newVersion: String,
            val downloadUrl: String,
            val publishDate: String,
        ) : GenericEvent

        class MonitoredItemMissing(
            val id: UUID,
            val timestamp: Instant,
            val monitorType: Monitor.Type,
            val monitorId: UUID,
            val monitoredItemId: String?
        ) : GenericEvent
    }
}

