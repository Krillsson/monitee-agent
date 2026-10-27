package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.check.CHECK_ID
import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.CheckType
import com.krillsson.sysapi.core.check.OTHER_CHECK_ID
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class CheckCertificateExpiryMonitorTest {

    private val now = Instant.parse("2026-08-01T10:00:00Z")

    private val monitor = CheckCertificateExpiryMonitor(
        id = UUID.randomUUID(),
        config = MonitorConfig(
            monitoredItemId = CHECK_ID.toString(),
            threshold = MonitoredValue.NumericalValue(7),
            inertia = Duration.ofSeconds(0),
            warningThreshold = MonitoredValue.NumericalValue(30)
        ),
        clock = Clock.fixed(now, ZoneOffset.UTC)
    )

    private fun input(vararg results: CheckResult): MonitorInput {
        val input = mockk<MonitorInput>()
        every { input.checkCertificateResults } returns results.toList()
        return input
    }

    private fun result(checkId: UUID, expiresAt: Instant?) = CheckResult(
        id = UUID.randomUUID(),
        checkId = checkId,
        checkType = CheckType.HTTP,
        timestamp = now,
        successful = true,
        latencyMs = 100,
        message = "OK",
        responseCode = 200,
        errorBody = null,
        resolvedValues = null,
        certificateExpiresAt = expiresAt,
        certificateIssuedAt = expiresAt?.minus(Duration.ofDays(90)),
        certificateIssuer = "R11",
        certificateSubject = "example.com"
    )

    @Test
    fun `takes the days until expiry of the check it was created for`() {
        // Given
        val given = input(
            result(OTHER_CHECK_ID, now.plus(Duration.ofDays(200))),
            result(CHECK_ID, now.plus(Duration.ofDays(23)))
        )

        // When
        val value = monitor.selectValue(given)

        // Then
        value shouldBe MonitoredValue.NumericalValue(23)
    }

    @Test
    fun `has no value when the result carries no certificate`() {
        // Given
        val given = input(result(CHECK_ID, null))

        // When
        val value = monitor.selectValue(given)

        // Then
        value shouldBe null
    }

    @Test
    fun `reports an expired certificate as negative days`() {
        // Given
        val given = input(result(CHECK_ID, now.minus(Duration.ofDays(2))))

        // When
        val value = monitor.selectValue(given)

        // Then
        value shouldBe MonitoredValue.NumericalValue(-2)
    }

    @Test
    fun `is past its threshold below it and not at it`() {
        // Given
        val below = MonitoredValue.NumericalValue(6)
        val at = MonitoredValue.NumericalValue(7)

        // When
        val pastBelow = monitor.isPastThreshold(below, monitor.config.threshold)
        val pastAt = monitor.isPastThreshold(at, monitor.config.threshold)

        // Then
        pastBelow shouldBe true
        pastAt shouldBe false
    }

    @ParameterizedTest
    @CsvSource(
        "45, NORMAL",
        "30, NORMAL",
        "29, WARNING",
        "7, WARNING",
        "6, CRITICAL",
        "-1, CRITICAL"
    )
    fun `warns below the warning threshold and escalates below the critical one`(days: Long, level: Monitor.Level) {
        // Given
        val value = MonitoredValue.NumericalValue(days)

        // When
        val actual = monitor.levelFor(value)

        // Then
        actual shouldBe level
    }

    @Test
    fun `bounds its value by the longest publicly trusted lifetime`() {
        // Given
        val input = mockk<MonitorMaxValueInput>()

        // When
        val max = monitor.maxValue(input)

        // Then
        max shouldBe MonitoredValue.NumericalValue(398)
    }
}
