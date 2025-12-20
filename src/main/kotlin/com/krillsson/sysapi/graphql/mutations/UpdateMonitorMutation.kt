package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.core.monitoring.Monitor
import java.util.*

data class UpdateNumericalMonitorInput(
        val monitorId: UUID,
        val inertiaInSeconds: Int?,
        val threshold: Long?,
)

data class UpdateFractionMonitorInput(
        val monitorId: UUID,
        val inertiaInSeconds: Int?,
        val threshold: Float?,
)

data class UpdateConditionalMonitorInput(
        val monitorId: UUID,
        val inertiaInSeconds: Int?,
        val threshold: Boolean?,
)

data class UpdateEnumMonitorInput(
        val monitorId: UUID,
        val type: Monitor.Type,
        val inertiaInSeconds: Int?,
        val threshold: String?,
)

interface UpdateMonitorOutput

data class UpdateMonitorOutputSucceeded(
        val monitorId: UUID,
) : UpdateMonitorOutput

data class UpdateMonitorOutputFailed(
        val reason: String
) : UpdateMonitorOutput