package com.krillsson.sysapi.workload

import java.time.Instant
import java.util.UUID

enum class PowerCommandJobState {
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class PowerCommandJob(
    val jobId: UUID,
    val platform: WorkloadPlatform,
    val id: String,
    val command: PowerCommand,
    val state: PowerCommandJobState,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val lastEvent: PowerCommandEvent
)
