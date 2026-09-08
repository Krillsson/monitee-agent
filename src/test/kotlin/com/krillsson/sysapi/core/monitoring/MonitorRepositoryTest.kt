package com.krillsson.sysapi.core.monitoring

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Duration
import org.junit.jupiter.api.Test

class MonitorRepositoryTest {

    private val store = mockk<MonitorStore>(relaxed = true)
    private val repository = MonitorRepository(store, MonitorFactory())

    private val inertia: Duration = Duration.ofMinutes(5)

    private fun storedMonitor(warningThreshold: Double?) = MonitorStore.StoredMonitor(
        id = MONITOR_ID,
        type = Monitor.Type.CPU_LOAD,
        config = MonitorStore.StoredMonitor.Config(null, 90.0, inertia, warningThreshold)
    )

    @Test
    fun `reads a monitor stored before warning thresholds existed as having none`() {
        // Given
        every { store.read() } returns listOf(storedMonitor(warningThreshold = null))

        // When
        val monitor = repository.read().single()

        // Then
        monitor.config.warningThreshold shouldBe null
    }

    @Test
    fun `reads a stored warning threshold as the monitor's own value kind`() {
        // Given
        every { store.read() } returns listOf(storedMonitor(warningThreshold = 70.0))

        // When
        val monitor = repository.read().single()

        // Then
        monitor.config.warningThreshold shouldBe 70f.toFractionalValue()
    }

    @Test
    fun `writes the warning threshold of a monitor`() {
        // Given
        val written = slot<List<MonitorStore.StoredMonitor>>()
        every { store.write(capture(written)) } returns Unit
        val monitor = MonitorFactory().createMonitor(
            Monitor.Type.CPU_LOAD,
            MONITOR_ID,
            MonitorConfig<MonitoredValue>(null, 90f.toFractionalValue(), inertia, 70f.toFractionalValue())
        )

        // When
        repository.write(listOf(monitor))

        // Then
        written.captured.single().config.warningThreshold shouldBe 70.0
    }
}
