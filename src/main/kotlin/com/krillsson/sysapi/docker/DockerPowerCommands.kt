package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.CommandType
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.domain.docker.displayName
import com.krillsson.sysapi.workload.PowerCommand

object DockerPowerCommands {

    private val COMMANDS: Map<State, Set<PowerCommand>> = mapOf(
        State.CREATED to setOf(PowerCommand.START),
        State.EXITED to setOf(PowerCommand.START),
        State.RUNNING to setOf(PowerCommand.SHUTDOWN, PowerCommand.FORCE_STOP, PowerCommand.REBOOT, PowerCommand.PAUSE),
        State.PAUSED to setOf(PowerCommand.FORCE_STOP, PowerCommand.RESUME),
        State.RESTARTING to setOf(PowerCommand.SHUTDOWN)
    )

    fun supported(container: Container): List<PowerCommand> = PowerCommand.entries.filter { refusal(container, it) == null }

    fun refusal(container: Container, command: PowerCommand): String? = when (command) {
        in COMMANDS[container.state].orEmpty() -> null
        else -> "${container.displayName()} is ${describe(container.state)} and does not accept $command"
    }

    fun commandType(command: PowerCommand): CommandType? = when (command) {
        PowerCommand.START -> CommandType.START
        PowerCommand.SHUTDOWN -> CommandType.STOP
        PowerCommand.FORCE_STOP -> null
        PowerCommand.REBOOT -> CommandType.RESTART
        PowerCommand.PAUSE -> CommandType.PAUSE
        PowerCommand.RESUME -> CommandType.UNPAUSE
    }

    fun reached(command: PowerCommand, state: State?): Boolean = when (command) {
        PowerCommand.START, PowerCommand.REBOOT, PowerCommand.RESUME -> state == State.RUNNING
        PowerCommand.SHUTDOWN, PowerCommand.FORCE_STOP -> state == State.EXITED
        PowerCommand.PAUSE -> state == State.PAUSED
    }

    fun describe(state: State?): String = when (state) {
        null -> "gone"
        State.CREATED -> "created"
        State.RESTARTING -> "restarting"
        State.RUNNING -> "running"
        State.PAUSED -> "paused"
        State.EXITED -> "stopped"
        State.DEAD -> "dead"
        State.UNKNOWN -> "in an unknown state"
    }
}
