package com.krillsson.sysapi.workload

import java.time.Instant
import java.util.UUID

interface PowerCommandEvent {
    val jobId: UUID
    val platform: WorkloadPlatform
    val id: String
    val timestamp: Instant
}

data class PowerCommandRunning(
    override val jobId: UUID,
    override val platform: WorkloadPlatform,
    override val id: String,
    override val timestamp: Instant,
    val platformTaskId: String?
) : PowerCommandEvent

data class PowerCommandSucceeded(
    override val jobId: UUID,
    override val platform: WorkloadPlatform,
    override val id: String,
    override val timestamp: Instant,
    val platformTaskId: String?,
    val exitStatus: String?
) : PowerCommandEvent

data class PowerCommandFailed(
    override val jobId: UUID,
    override val platform: WorkloadPlatform,
    override val id: String,
    override val timestamp: Instant,
    val platformTaskId: String?,
    val reason: String,
    val log: List<String>,
    val canForceStop: Boolean
) : PowerCommandEvent
