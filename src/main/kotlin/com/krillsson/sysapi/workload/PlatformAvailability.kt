package com.krillsson.sysapi.workload

import java.time.Instant

data class PlatformAvailability(
    val platform: WorkloadPlatform,
    val available: Boolean,
    val isDisabled: Boolean,
    val reason: String?,
    val lastSuccessfulPoll: Instant?
)
