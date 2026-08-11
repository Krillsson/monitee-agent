package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.check.CHECK_ID
import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.CheckType
import com.krillsson.sysapi.core.check.HttpCheck
import com.krillsson.sysapi.core.check.OTHER_CHECK_ID
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CheckLatencyMonitorTest {

    private val monitor = CheckLatencyMonitor(
        id = UUID.randomUUID(),
        config = MonitorConfig(
            monitoredItemId = CHECK_ID.toString(),
            threshold = MonitoredValue.NumericalValue(2000),
            inertia = Duration.ofSeconds(0)
        )
    )

    private fun input(vararg results: CheckResult): MonitorInput {
        val input = mockk<MonitorInput>()
        every { input.checkResults } returns results.toList()
        return input
    }

    private fun result(checkId: UUID, latencyMs: Long, successful: Boolean = true) = CheckResult(
        id = UUID.randomUUID(),
        checkId = checkId,
        checkType = CheckType.HTTP,
        timestamp = Instant.parse("2026-08-01T10:00:00Z"),
        successful = successful,
        latencyMs = latencyMs,
        message = "OK",
        responseCode = 200,
        errorBody = null,
        resolvedValues = null
    )

    @Test
    fun `takes the latency of the check it was created for`() {
        // Given
        val given = input(result(OTHER_CHECK_ID, 5000), result(CHECK_ID, 1500))

        // When
        val value = monitor.selectValue(given)

        // Then
        value shouldBe MonitoredValue.NumericalValue(1500)
    }

    @Test
    fun `ignores the latency of a check that failed`() {
        // Given
        val given = input(result(CHECK_ID, 20000, successful = false))

        // When
        val value = monitor.selectValue(given)

        // Then
        value shouldBe null
    }

    @Test
    fun `is past its threshold above it and not at it`() {
        // Given
        val above = MonitoredValue.NumericalValue(2001)
        val at = MonitoredValue.NumericalValue(2000)

        // When
        val pastAbove = monitor.isPastThreshold(above)
        val pastAt = monitor.isPastThreshold(at)

        // Then
        pastAbove shouldBe true
        pastAt shouldBe false
    }

    @Test
    fun `takes the timeout of the check as its highest value`() {
        // Given
        val check = mockk<HttpCheck>()
        every { check.id } returns CHECK_ID
        every { check.timeoutSeconds } returns 20
        val input = mockk<MonitorMaxValueInput>()
        every { input.checks } returns listOf(check)

        // When
        val max = monitor.maxValue(input)

        // Then
        max shouldBe MonitoredValue.NumericalValue(20000)
    }
}
