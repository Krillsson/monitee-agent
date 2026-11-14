package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.WorkloadPlatform
import java.util.UUID

data class PerformPowerCommandInput(
    val platform: WorkloadPlatform,
    val id: String,
    val command: PowerCommand
)

interface PerformPowerCommandOutput

data class PerformPowerCommandOutputStarted(
    val jobId: UUID,
    val platform: WorkloadPlatform,
    val id: String
) : PerformPowerCommandOutput

data class PerformPowerCommandOutputFailed(
    val reason: String
) : PerformPowerCommandOutput
