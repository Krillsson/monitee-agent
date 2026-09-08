package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.monitoring.monitors.CpuMonitor
import com.krillsson.sysapi.core.monitoring.monitors.FileSystemSpaceMonitor
import com.krillsson.sysapi.core.monitoring.monitors.NetworkUpMonitor
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class MonitorTest {

    private val inertia = Duration.ofMinutes(5)

    private fun cpuMonitor(warningThreshold: Float?) = CpuMonitor(
        UUID.randomUUID(),
        MonitorConfig(null, 90f.toFractionalValue(), inertia, warningThreshold?.toFractionalValue())
    )

    private fun fileSystemMonitor(warningThreshold: Long?) = FileSystemSpaceMonitor(
        UUID.randomUUID(),
        MonitorConfig(MONITORED_ITEM_ID, 10L.toNumericalValue(), inertia, warningThreshold?.toNumericalValue())
    )

    @ParameterizedTest
    @CsvSource("50, NORMAL", "70, NORMAL", "71, WARNING", "90, WARNING", "91, CRITICAL")
    fun `climbs the level ladder for a monitor that alerts on a rising value`(
        value: Float,
        expected: Monitor.Level
    ) {
        // Given
        val monitor = cpuMonitor(warningThreshold = 70f)

        // When
        val level = monitor.levelFor(value.toFractionalValue())

        // Then
        level shouldBe expected
    }

    @ParameterizedTest
    @CsvSource("100, NORMAL", "50, NORMAL", "49, WARNING", "10, WARNING", "9, CRITICAL")
    fun `climbs the level ladder for a monitor that alerts on a falling value`(
        value: Long,
        expected: Monitor.Level
    ) {
        // Given
        val monitor = fileSystemMonitor(warningThreshold = 50L)

        // When
        val level = monitor.levelFor(value.toNumericalValue())

        // Then
        level shouldBe expected
    }

    @Test
    fun `never reports a warning when no warning threshold is configured`() {
        // Given
        val monitor = cpuMonitor(warningThreshold = null)

        // When
        val belowThreshold = monitor.levelFor(89f.toFractionalValue())
        val aboveThreshold = monitor.levelFor(91f.toFractionalValue())

        // Then
        belowThreshold shouldBe Monitor.Level.NORMAL
        aboveThreshold shouldBe Monitor.Level.CRITICAL
    }

    @Test
    fun `only numerical and fractional monitors support a warning threshold`() {
        // Given
        val conditional = NetworkUpMonitor(
            UUID.randomUUID(),
            MonitorConfig(MONITORED_ITEM_ID, true.toConditionalValue(), inertia)
        )

        // Then
        cpuMonitor(warningThreshold = null).supportsWarningThreshold() shouldBe true
        fileSystemMonitor(warningThreshold = null).supportsWarningThreshold() shouldBe true
        conditional.supportsWarningThreshold() shouldBe false
    }
}
