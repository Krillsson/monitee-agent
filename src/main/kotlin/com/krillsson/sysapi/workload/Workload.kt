package com.krillsson.sysapi.workload

data class Workload(
    val kind: WorkloadKind,
    val platform: WorkloadPlatform,
    val id: String,
    val name: String,
    val runState: RunState,
    val source: Any
)
