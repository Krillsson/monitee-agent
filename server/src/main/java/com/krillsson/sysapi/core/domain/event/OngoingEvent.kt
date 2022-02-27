package com.krillsson.sysapi.core.domain.event

import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.OffsetDateTime
import java.util.*

class OngoingEvent(
    id: UUID,
    monitorId: UUID,
    monitoredItemId: String?,
    monitorType: Monitor.Type,
    startTime: OffsetDateTime,
    threshold: MonitoredValue,
    value: MonitoredValue
) : Event(id, monitorId, monitoredItemId, monitorType, startTime, threshold, value)
