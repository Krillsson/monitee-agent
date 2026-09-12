package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInputCreator
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class DiskWriteRateMonitorTest {

    private val monitor = DiskWriteRateMonitor(
        id = UUID.randomUUID(),
        config = MonitorConfig(
            monitoredItemId = "disk0",
            threshold = MonitoredValue.NumericalValue(1000),
            inertia = Duration.ofSeconds(0)
        )
    )

    @Test
    fun `takes the theoretical drive write speed limit as its highest value`() {
        // Given
        val input = mockk<MonitorMaxValueInput>()

        // When
        val max = monitor.maxValue(input)

        // Then
        max shouldBe MonitorInputCreator.THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND.toNumericalValue()
    }
}
