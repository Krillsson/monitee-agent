package com.krillsson.sysapi.core.domain.event

import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.Instant
import java.util.*

class PastEvent(
    id: UUID,
    monitorId: UUID,
    monitoredItemId: String?,
    startTime: Instant,
    val endTime: Instant,
    type: Monitor.Type,
    threshold: MonitoredValue,
    endValue: MonitoredValue,
    val startValue: MonitoredValue
) : Event(id, monitorId, monitoredItemId, type, startTime, threshold, endValue)