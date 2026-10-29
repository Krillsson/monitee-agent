package com.krillsson.sysapi.core.domain.event

import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.Instant
import java.util.*

class OngoingEvent(
    id: UUID,
    monitorId: UUID,
    monitoredItemId: String?,
    monitorType: Monitor.Type,
    startTime: Instant,
    threshold: MonitoredValue,
    value: MonitoredValue,
    severity: EventSeverity,
    acknowledgedAt: Instant? = null,
    acknowledgementNote: String? = null,
    val notified: Boolean = true
) : Event(
    id,
    monitorId,
    monitoredItemId,
    monitorType,
    startTime,
    threshold,
    value,
    severity,
    acknowledgedAt,
    acknowledgementNote
)
