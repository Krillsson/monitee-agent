package com.krillsson.sysapi.workload

import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.unraid.UnraidVmState

object RunStates {

    fun docker(state: State): RunState = when (state) {
        State.RUNNING -> RunState.RUNNING
        State.PAUSED -> RunState.PAUSED
        State.CREATED, State.EXITED -> RunState.STOPPED
        State.RESTARTING -> RunState.STARTING
        State.DEAD -> RunState.FAILED
        State.UNKNOWN -> RunState.UNKNOWN
    }

    fun proxmox(status: ProxmoxGuestStatus): RunState = when (status) {
        ProxmoxGuestStatus.RUNNING -> RunState.RUNNING
        ProxmoxGuestStatus.STOPPED -> RunState.STOPPED
        ProxmoxGuestStatus.UNKNOWN -> RunState.UNKNOWN
    }

    fun unraid(state: UnraidVmState): RunState = when (state) {
        UnraidVmState.RUNNING, UnraidVmState.IDLE -> RunState.RUNNING
        UnraidVmState.PAUSED, UnraidVmState.PMSUSPENDED -> RunState.PAUSED
        UnraidVmState.SHUTOFF -> RunState.STOPPED
        UnraidVmState.SHUTDOWN -> RunState.STOPPING
        UnraidVmState.CRASHED -> RunState.FAILED
        UnraidVmState.NOSTATE -> RunState.UNKNOWN
    }
}
