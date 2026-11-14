package com.krillsson.sysapi.workload

import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RunStatesTest {

    @ParameterizedTest
    @CsvSource(
        "RUNNING, RUNNING",
        "PAUSED, PAUSED",
        "CREATED, STOPPED",
        "EXITED, STOPPED",
        "RESTARTING, STARTING",
        "DEAD, FAILED",
        "UNKNOWN, UNKNOWN"
    )
    fun `maps a docker state to the run state the app draws`(state: State, expected: RunState) {
        // When
        val runState = RunStates.docker(state)

        // Then
        runState shouldBe expected
    }

    @ParameterizedTest
    @CsvSource(
        "RUNNING, RUNNING",
        "STOPPED, STOPPED",
        "UNKNOWN, UNKNOWN"
    )
    fun `maps a proxmox guest status to the run state the app draws`(status: ProxmoxGuestStatus, expected: RunState) {
        // When
        val runState = RunStates.proxmox(status)

        // Then
        runState shouldBe expected
    }
}
