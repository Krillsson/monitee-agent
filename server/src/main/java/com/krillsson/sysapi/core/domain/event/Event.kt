package com.krillsson.sysapi.core.domain.event

import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.OffsetDateTime
import java.util.*

abstract class Event(
    val id: UUID,
    val monitorId: UUID,
    val monitoredItemId: String? = null,
    val monitorType: Monitor.Type,
    val startTime: OffsetDateTime,
    val threshold: MonitoredValue,
    val value: MonitoredValue
)