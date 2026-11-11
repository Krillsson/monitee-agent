package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.util.UUID

class ProxmoxGuestMonitorsTest {

    private val running = ProxmoxGuestRunningMonitor(
        UUID.randomUUID(),
        MonitorConfig("100", MonitoredValue.ConditionalValue(true), Duration.ZERO)
    )
    private val cpu = ProxmoxGuestCpuMonitor(
        UUID.randomUUID(),
        MonitorConfig("100", MonitoredValue.FractionalValue(80f), Duration.ZERO)
    )
    private val memory = ProxmoxGuestMemoryMonitor(
        UUID.randomUUID(),
        MonitorConfig("100", MonitoredValue.NumericalValue(900), Duration.ZERO)
    )

    private fun input(guests: List<ProxmoxGuest>?): MonitorInput {
        val input = mockk<MonitorInput>()
        every { input.proxmoxGuests } returns guests
        return input
    }

    @ParameterizedTest
    @CsvSource("RUNNING, true", "STOPPED, false", "UNKNOWN, false")
    fun `reads a guest as running only when proxmox says so`(status: ProxmoxGuestStatus, expected: Boolean) {
        // Given
        val given = input(listOf(guest(101), guest(100, status = status)))

        // When
        val value = running.selectValue(given)

        // Then
        value shouldBe MonitoredValue.ConditionalValue(expected)
        running.levelFor(value!!) shouldBe if (expected) Monitor.Level.NORMAL else Monitor.Level.CRITICAL
    }

    @Test
    fun `reads the cpu and memory of the monitored guest`() {
        // Given
        val given = input(listOf(guest(101), guest(100)))

        // When
        val cpuValue = cpu.selectValue(given)
        val memoryValue = memory.selectValue(given)

        // Then
        cpuValue shouldBe MonitoredValue.FractionalValue(25f)
        memoryValue shouldBe MonitoredValue.NumericalValue(512)
    }

    @Test
    fun `reads a stopped guest as idle rather than missing`() {
        // Given
        val given = input(listOf(guest(100, status = ProxmoxGuestStatus.STOPPED)))

        // When
        val cpuValue = cpu.selectValue(given)
        val memoryValue = memory.selectValue(given)

        // Then
        cpuValue shouldBe MonitoredValue.FractionalValue(0f)
        memoryValue shouldBe MonitoredValue.NumericalValue(0)
    }

    @Test
    fun `has no value for a guest proxmox no longer lists`() {
        // Given
        val given = input(listOf(guest(101)))

        // When
        val value = running.selectValue(given)

        // Then
        value shouldBe null
        running.canEvaluate(given) shouldBe true
    }

    @Test
    fun `is not evaluated while proxmox is unavailable`() {
        // Given
        val given = input(null)

        // When
        val evaluated = listOf(running.canEvaluate(given), cpu.canEvaluate(given), memory.canEvaluate(given))

        // Then
        evaluated shouldBe listOf(false, false, false)
    }

    @Test
    fun `caps memory at what the guest is given`() {
        // Given
        val maxValueInput = mockk<MonitorMaxValueInput>()
        every { maxValueInput.proxmoxGuests } returns listOf(guest(100))

        // When
        val max = memory.maxValue(maxValueInput)

        // Then
        max shouldBe MonitoredValue.NumericalValue(1024)
    }
}
