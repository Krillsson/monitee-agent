package com.krillsson.sysapi.core.domain.event

import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.OffsetDateTime
import java.util.*

class PastEvent(
    id: UUID,
    monitorId: UUID,
    monitoredItemId: String?,
    startTime: OffsetDateTime,
    val endTime: OffsetDateTime,
    type: Monitor.Type,
    threshold: Double,
    value: Double
) : Event(id, monitorId, monitoredItemId, type, startTime, threshold, value)