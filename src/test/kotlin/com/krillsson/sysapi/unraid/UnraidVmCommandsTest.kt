package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.unraid.UnraidClient.Result
import com.krillsson.sysapi.workload.PowerCommand
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UnraidVmCommandsTest {

    @ParameterizedTest
    @CsvSource(
        "SHUTOFF, START, true",
        "SHUTOFF, SHUTDOWN, false",
        "SHUTOFF, FORCE_STOP, false",
        "SHUTOFF, REBOOT, false",
        "SHUTOFF, PAUSE, false",
        "SHUTOFF, RESUME, false",
        "RUNNING, START, false",
        "RUNNING, SHUTDOWN, true",
        "RUNNING, FORCE_STOP, true",
        "RUNNING, REBOOT, true",
        "RUNNING, PAUSE, true",
        "RUNNING, RESUME, false",
        "IDLE, START, false",
        "IDLE, SHUTDOWN, true",
        "IDLE, FORCE_STOP, true",
        "IDLE, REBOOT, true",
        "IDLE, PAUSE, false",
        "IDLE, RESUME, false",
        "PAUSED, START, false",
        "PAUSED, SHUTDOWN, false",
        "PAUSED, FORCE_STOP, true",
        "PAUSED, REBOOT, false",
        "PAUSED, PAUSE, false",
        "PAUSED, RESUME, true",
        "PMSUSPENDED, START, false",
        "PMSUSPENDED, SHUTDOWN, false",
        "PMSUSPENDED, FORCE_STOP, true",
        "PMSUSPENDED, REBOOT, false",
        "PMSUSPENDED, PAUSE, false",
        "PMSUSPENDED, RESUME, false",
        "CRASHED, START, false",
        "CRASHED, SHUTDOWN, false",
        "CRASHED, FORCE_STOP, true",
        "CRASHED, REBOOT, false",
        "CRASHED, PAUSE, false",
        "CRASHED, RESUME, false",
        "SHUTDOWN, START, false",
        "SHUTDOWN, SHUTDOWN, false",
        "SHUTDOWN, FORCE_STOP, true",
        "SHUTDOWN, REBOOT, false",
        "SHUTDOWN, PAUSE, false",
        "SHUTDOWN, RESUME, false",
        "NOSTATE, START, false",
        "NOSTATE, SHUTDOWN, false",
        "NOSTATE, FORCE_STOP, false",
        "NOSTATE, REBOOT, false",
        "NOSTATE, PAUSE, false",
        "NOSTATE, RESUME, false"
    )
    fun `accepts only what unraid would act on in each state`(state: UnraidVmState, command: PowerCommand, accepted: Boolean) {
        // Given
        val vm = unraidVm(state = state)

        // When
        val supported = UnraidVmCommands.supported(vm)
        val refusal = UnraidVmCommands.refusal(vm, command)

        // Then
        (command in supported) shouldBe accepted
        (refusal == null) shouldBe accepted
    }

    @ParameterizedTest
    @CsvSource(
        "SHUTOFF, SHUTDOWN, Windows 11 is off and does not accept SHUTDOWN",
        "RUNNING, START, Windows 11 is running and does not accept START",
        "SHUTDOWN, REBOOT, Windows 11 is shutting down and does not accept REBOOT"
    )
    fun `says which state keeps a vm from taking a command`(state: UnraidVmState, command: PowerCommand, reason: String) {
        // When
        val refusal = UnraidVmCommands.refusal(unraidVm(state = state), command)

        // Then
        refusal shouldBe reason
    }

    @ParameterizedTest
    @CsvSource(
        "START, start",
        "SHUTDOWN, stop",
        "FORCE_STOP, forceStop",
        "REBOOT, reboot",
        "PAUSE, pause",
        "RESUME, resume"
    )
    fun `sends each command as the unraid mutation of its own`(command: PowerCommand, mutation: String) {
        // When
        val sent = UnraidVmCommands.mutation(command)

        // Then
        sent shouldBe mutation
    }

    @ParameterizedTest
    @CsvSource(
        "START, RUNNING, true",
        "START, IDLE, true",
        "START, SHUTOFF, false",
        "REBOOT, RUNNING, true",
        "RESUME, PAUSED, false",
        "SHUTDOWN, SHUTOFF, true",
        "SHUTDOWN, SHUTDOWN, false",
        "FORCE_STOP, SHUTOFF, true",
        "PAUSE, PAUSED, true",
        "PAUSE, RUNNING, false",
        "START, , false"
    )
    fun `knows the state each command ends in`(command: PowerCommand, state: UnraidVmState?, reached: Boolean) {
        // When
        val isReached = UnraidVmCommands.reached(command, state)

        // Then
        isReached shouldBe reached
    }

    @ParameterizedTest
    @CsvSource(
        "REBOOT, 'Failed to reboot VM: Graceful shutdown failed, please force stop the VM and try again', true",
        "REBOOT, 'Failed to reboot VM: domain is not running', false",
        "SHUTDOWN, 'Graceful shutdown failed', false"
    )
    fun `offers a force stop only after a reboot that could not shut the vm down`(command: PowerCommand, message: String, canForceStop: Boolean) {
        // When
        val offered = UnraidVmCommands.canForceStop(command, Result.GraphQlErrors(listOf(message), emptyList()))

        // Then
        offered shouldBe canForceStop
        UnraidVmCommands.canForceStop(command, Result.Forbidden("Forbidden")) shouldBe false
    }
}
