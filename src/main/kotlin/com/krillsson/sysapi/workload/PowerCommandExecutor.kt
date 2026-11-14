package com.krillsson.sysapi.workload

interface PowerCommandExecutor {

    sealed interface Admission {
        data class Admitted(val name: String) : Admission
        data class Refused(val reason: String) : Admission
    }

    val platform: WorkloadPlatform

    fun admit(id: String, command: PowerCommand): Admission

    fun run(id: String, command: PowerCommand, progress: PowerCommandProgress)
}

interface PowerCommandProgress {
    fun running(platformTaskId: String)

    fun succeeded(platformTaskId: String? = null, exitStatus: String? = null)

    fun failed(reason: String, platformTaskId: String? = null, log: List<String> = emptyList(), canForceStop: Boolean = false)
}
