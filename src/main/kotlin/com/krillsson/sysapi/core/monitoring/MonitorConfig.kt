package com.krillsson.sysapi.core.monitoring

import java.time.Duration

data class MonitorConfig<T : MonitoredValue>(
    val monitoredItemId: String? = null,
    val threshold: T,
    val inertia: Duration
)