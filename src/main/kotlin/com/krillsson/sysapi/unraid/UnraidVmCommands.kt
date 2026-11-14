package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.unraid.UnraidClient.Result
import com.krillsson.sysapi.workload.PowerCommand

object UnraidVmCommands {

    private const val GRACEFUL_SHUTDOWN_FAILED = "Graceful shutdown failed"
    private val RUNNING_STATES = setOf(UnraidVmState.RUNNING, UnraidVmState.IDLE)

    private val COMMANDS: Map<UnraidVmState, Set<PowerCommand>> = mapOf(
        UnraidVmState.SHUTOFF to setOf(PowerCommand.START),
        UnraidVmState.RUNNING to setOf(PowerCommand.SHUTDOWN, PowerCommand.FORCE_STOP, PowerCommand.REBOOT, PowerCommand.PAUSE),
        UnraidVmState.IDLE to setOf(PowerCommand.SHUTDOWN, PowerCommand.FORCE_STOP, PowerCommand.REBOOT),
        UnraidVmState.PAUSED to setOf(PowerCommand.FORCE_STOP, PowerCommand.RESUME),
        UnraidVmState.PMSUSPENDED to setOf(PowerCommand.FORCE_STOP),
        UnraidVmState.CRASHED to setOf(PowerCommand.FORCE_STOP),
        UnraidVmState.SHUTDOWN to setOf(PowerCommand.FORCE_STOP)
    )

    fun supported(vm: UnraidVm): List<PowerCommand> = PowerCommand.entries.filter { refusal(vm, it) == null }

    fun refusal(vm: UnraidVm, command: PowerCommand): String? = when (command) {
        in COMMANDS[vm.state].orEmpty() -> null
        else -> "${vm.name} is ${describe(vm.state)} and does not accept $command"
    }

    fun mutation(command: PowerCommand): String = when (command) {
        PowerCommand.START -> "start"
        PowerCommand.SHUTDOWN -> "stop"
        PowerCommand.FORCE_STOP -> "forceStop"
        PowerCommand.REBOOT -> "reboot"
        PowerCommand.PAUSE -> "pause"
        PowerCommand.RESUME -> "resume"
    }

    fun reached(command: PowerCommand, state: UnraidVmState?): Boolean = when (command) {
        PowerCommand.START, PowerCommand.RESUME, PowerCommand.REBOOT -> state in RUNNING_STATES
        PowerCommand.SHUTDOWN, PowerCommand.FORCE_STOP -> state == UnraidVmState.SHUTOFF
        PowerCommand.PAUSE -> state == UnraidVmState.PAUSED
    }

    fun canForceStop(command: PowerCommand, result: Result.Failure): Boolean =
        command == PowerCommand.REBOOT && result is Result.GraphQlErrors &&
            result.messages.any { it.contains(GRACEFUL_SHUTDOWN_FAILED, ignoreCase = true) }

    fun describe(state: UnraidVmState?): String = when (state) {
        null -> "no longer listed"
        UnraidVmState.NOSTATE -> "in no known state"
        UnraidVmState.RUNNING, UnraidVmState.IDLE -> "running"
        UnraidVmState.PAUSED -> "paused"
        UnraidVmState.SHUTDOWN -> "shutting down"
        UnraidVmState.SHUTOFF -> "off"
        UnraidVmState.CRASHED -> "crashed"
        UnraidVmState.PMSUSPENDED -> "suspended by its guest"
    }
}
