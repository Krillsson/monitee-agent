package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.unraid.HOME_ASSISTANT_UUID
import com.krillsson.sysapi.unraid.UnraidVm
import com.krillsson.sysapi.unraid.UnraidVmState
import com.krillsson.sysapi.unraid.WINDOWS_UUID
import com.krillsson.sysapi.unraid.unraidVm
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.util.UUID

class UnraidVmRunningMonitorTest {

    private val monitor = UnraidVmRunningMonitor(
        UUID.randomUUID(),
        MonitorConfig(WINDOWS_UUID, MonitoredValue.ConditionalValue(true), Duration.ZERO)
    )

    private fun input(vms: List<UnraidVm>?): MonitorInput {
        val input = mockk<MonitorInput>()
        every { input.unraidVms } returns vms
        return input
    }

    @ParameterizedTest
    @CsvSource(
        "RUNNING, true",
        "IDLE, true",
        "PAUSED, false",
        "PMSUSPENDED, false",
        "SHUTDOWN, false",
        "SHUTOFF, false",
        "CRASHED, false",
        "NOSTATE, false"
    )
    fun `reads a vm as running only while it runs`(state: UnraidVmState, expected: Boolean) {
        // Given
        val given = input(listOf(unraidVm(HOME_ASSISTANT_UUID), unraidVm(WINDOWS_UUID, state = state)))

        // When
        val value = monitor.selectValue(given)

        // Then
        value shouldBe MonitoredValue.ConditionalValue(expected)
        monitor.levelFor(value!!) shouldBe if (expected) Monitor.Level.NORMAL else Monitor.Level.CRITICAL
    }

    @Test
    fun `has no value for a vm unraid no longer lists`() {
        // Given
        val given = input(listOf(unraidVm(HOME_ASSISTANT_UUID)))

        // When
        val value = monitor.selectValue(given)

        // Then
        value shouldBe null
        monitor.canEvaluate(given) shouldBe true
    }

    @Test
    fun `is not evaluated while the vm list could not be read`() {
        // Given
        val given = input(null)

        // When
        val evaluated = monitor.canEvaluate(given)

        // Then
        evaluated shouldBe false
    }
}
