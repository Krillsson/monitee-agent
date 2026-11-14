package com.krillsson.sysapi.unraid

import java.time.Instant

enum class UnraidVmState {
    NOSTATE,
    RUNNING,
    IDLE,
    PAUSED,
    SHUTDOWN,
    SHUTOFF,
    CRASHED,
    PMSUSPENDED
}

data class UnraidVm(
    val id: String,
    val name: String,
    val state: UnraidVmState
) {
    fun isRunning(): Boolean = state == UnraidVmState.RUNNING || state == UnraidVmState.IDLE
}

data class UnraidSnapshot(
    val vms: List<UnraidVm>,
    val vmsUnavailableReason: String?,
    val lastSuccessfulPoll: Instant
)
