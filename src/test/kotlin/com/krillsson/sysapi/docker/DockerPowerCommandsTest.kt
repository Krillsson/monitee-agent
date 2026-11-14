package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.CommandType
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.workload.PowerCommand
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DockerPowerCommandsTest {

    @ParameterizedTest
    @CsvSource(
        "CREATED, START",
        "EXITED, START",
        "RUNNING, 'SHUTDOWN,FORCE_STOP,REBOOT,PAUSE'",
        "PAUSED, 'FORCE_STOP,RESUME'",
        "RESTARTING, SHUTDOWN",
        "DEAD, ''",
        "UNKNOWN, ''"
    )
    fun `accepts only what docker would act on in each state`(state: State, accepted: String) {
        // Given
        val container = container("abc", "web").copy(state = state)

        // When
        val supported = DockerPowerCommands.supported(container)

        // Then
        supported.joinToString(",") shouldBe accepted
        PowerCommand.entries.filter { DockerPowerCommands.refusal(container, it) == null }.joinToString(",") shouldBe accepted
    }

    @ParameterizedTest
    @CsvSource(
        "RUNNING, START, web is running and does not accept START",
        "EXITED, SHUTDOWN, web is stopped and does not accept SHUTDOWN",
        "PAUSED, PAUSE, web is paused and does not accept PAUSE",
        "DEAD, START, web is dead and does not accept START"
    )
    fun `says which state keeps a container from taking a command`(state: State, command: PowerCommand, reason: String) {
        // When
        val refusal = DockerPowerCommands.refusal(container("abc", "web").copy(state = state), command)

        // Then
        refusal shouldBe reason
    }

    @ParameterizedTest
    @CsvSource(
        "START, START",
        "SHUTDOWN, STOP",
        "FORCE_STOP, ",
        "REBOOT, RESTART",
        "PAUSE, PAUSE",
        "RESUME, UNPAUSE"
    )
    fun `runs each command as the docker command of its own, and a force stop as a kill`(command: PowerCommand, type: CommandType?) {
        // When
        val sent = DockerPowerCommands.commandType(command)

        // Then
        sent shouldBe type
    }

    @ParameterizedTest
    @CsvSource(
        "START, RUNNING, true",
        "START, EXITED, false",
        "REBOOT, RUNNING, true",
        "RESUME, RUNNING, true",
        "SHUTDOWN, EXITED, true",
        "FORCE_STOP, EXITED, true",
        "FORCE_STOP, RUNNING, false",
        "PAUSE, PAUSED, true",
        "SHUTDOWN, , false"
    )
    fun `knows the state each command ends in`(command: PowerCommand, state: State?, reached: Boolean) {
        // When
        val isReached = DockerPowerCommands.reached(command, state)

        // Then
        isReached shouldBe reached
    }
}
