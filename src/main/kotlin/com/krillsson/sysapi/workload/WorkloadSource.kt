package com.krillsson.sysapi.workload

interface WorkloadSource {
    val platform: WorkloadPlatform
    val kinds: Set<WorkloadKind>

    fun read(): Reading

    data class Reading(
        val availability: PlatformAvailability,
        val workloads: List<Workload>
    )
}
