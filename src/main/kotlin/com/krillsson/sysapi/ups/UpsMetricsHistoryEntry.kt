package com.krillsson.sysapi.ups

import java.time.Instant

data class UpsMetricsHistoryEntry(
    val id: String,
    val timestamp: Instant,
    val metrics: UpsDevice.Metrics
)