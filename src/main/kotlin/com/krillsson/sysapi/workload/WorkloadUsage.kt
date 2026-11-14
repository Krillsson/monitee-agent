package com.krillsson.sysapi.workload

import java.time.Instant

data class WorkloadUsage(
    val cpuPercent: Double,
    val memoryUsedBytes: Long,
    val memoryLimitBytes: Long?,
    val sampledAt: Instant
)
