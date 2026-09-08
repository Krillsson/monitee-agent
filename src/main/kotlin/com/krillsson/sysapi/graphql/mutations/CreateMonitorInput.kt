package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.core.monitoring.Monitor

data class CreateNumericalMonitorInput(
        val inertiaInSeconds: Int,
        val type: Monitor.Type,
        val threshold: Long,
        val monitoredItemId: String?,
        val warningThreshold: Long? = null
)

data class CreateFractionMonitorInput(
        val inertiaInSeconds: Int,
        val type: Monitor.Type,
        val threshold: Float,
        val monitoredItemId: String?,
        val warningThreshold: Float? = null
)

data class CreateConditionalMonitorInput(
        val inertiaInSeconds: Int,
        val type: Monitor.Type,
        val threshold: Boolean,
        val monitoredItemId: String?
)

data class CreateEnumMonitorInput(
    val inertiaInSeconds: Int,
    val type: Monitor.Type,
    val threshold: String,
    val monitoredItemId: String?
)