package com.krillsson.sysapi.workload

data class WorkloadList(
    val workloads: List<Workload>,
    val platforms: List<PlatformAvailability>
) {
    fun all(): List<Any> = workloads.map { it.source }

    fun running(): List<Any> = workloads.filter { it.runState == RunState.RUNNING }.map { it.source }

    fun find(platform: WorkloadPlatform, id: String): Any? =
        workloads.firstOrNull { it.platform == platform && it.id == id }?.source
}
