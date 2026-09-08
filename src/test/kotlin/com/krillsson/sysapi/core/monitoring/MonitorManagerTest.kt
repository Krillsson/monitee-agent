package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.monitoring.event.EventManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Test

class MonitorManagerTest {

    private val inertia = Duration.ofMinutes(5)

    private fun manager(
        repository: MonitorRepository,
        eventManager: EventManager = mockk(relaxed = true)
    ) = MonitorManager(
        metrics = mockk(relaxed = true),
        eventManager = eventManager,
        repository = repository,
        monitoredItemMissingChecker = mockk(relaxed = true),
        clock = Clock.systemUTC(),
        monitorInputCreator = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        monitorFactory = MonitorFactory()
    ).also { it.start() }

    @Test
    fun `purges events left behind by monitors that were not restored from disk`() {
        // Given
        val restoredMonitorId = UUID.randomUUID()
        val restoredMonitor = mockk<Monitor<MonitoredValue>>(relaxed = true)
        every { restoredMonitor.id } returns restoredMonitorId

        val eventManager = mockk<EventManager>(relaxed = true)
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns listOf(restoredMonitor)

        // When
        manager(repository, eventManager)

        // Then
        verify { eventManager.removeEventsForMonitorsNotIn(setOf(restoredMonitorId)) }
    }

    @Test
    fun `stores the warning threshold of a new monitor`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()
        val written = slot<List<Monitor<MonitoredValue>>>()
        every { repository.write(capture(written)) } returns Unit

        // When
        manager(repository).add(inertia, Monitor.Type.CPU_LOAD, 90f.toFractionalValue(), null, 70f.toFractionalValue())

        // Then
        written.captured.single().config.warningThreshold shouldBe 70f.toFractionalValue()
    }

    @Test
    fun `rejects a warning threshold that would not trigger before the threshold`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()

        // When
        val thrown = shouldThrow<IllegalArgumentException> {
            manager(repository).add(
                inertia,
                Monitor.Type.CPU_LOAD,
                90f.toFractionalValue(),
                null,
                95f.toFractionalValue()
            )
        }

        // Then
        thrown.message shouldBe "Warning threshold FractionalValue(value=95.0) would trigger no earlier " +
            "than threshold FractionalValue(value=90.0)"
    }

    @Test
    fun `accepts a warning threshold above the threshold for a monitor that alerts on a falling value`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()
        val written = slot<List<Monitor<MonitoredValue>>>()
        every { repository.write(capture(written)) } returns Unit

        // When
        manager(repository).add(
            inertia,
            Monitor.Type.MEMORY_SPACE,
            10L.toNumericalValue(),
            null,
            50L.toNumericalValue()
        )

        // Then
        written.captured.single().config.warningThreshold shouldBe 50L.toNumericalValue()
    }

    @Test
    fun `rejects a warning threshold on a monitor type that has no level between normal and alerting`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()

        // When
        val thrown = shouldThrow<IllegalArgumentException> {
            manager(repository).add(
                inertia,
                Monitor.Type.NETWORK_UP,
                true.toConditionalValue(),
                MONITORED_ITEM_ID,
                true.toConditionalValue()
            )
        }

        // Then
        thrown.message shouldBe "NETWORK_UP does not support a warning threshold"
    }

    @Test
    fun `clears a warning threshold on update`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()
        val written = slot<List<Monitor<MonitoredValue>>>()
        every { repository.write(capture(written)) } returns Unit
        val manager = manager(repository)
        val id = manager.add(inertia, Monitor.Type.CPU_LOAD, 90f.toFractionalValue(), null, 70f.toFractionalValue())

        // When
        manager.update(id, null, null, null, clearWarningThreshold = true)

        // Then
        written.captured.single().config.warningThreshold shouldBe null
    }

    @Test
    fun `refuses to both set and clear a warning threshold in one update`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()
        val manager = manager(repository)
        val id = manager.add(inertia, Monitor.Type.CPU_LOAD, 90f.toFractionalValue(), null)

        // When
        val thrown = shouldThrow<IllegalArgumentException> {
            manager.update(id, null, null, 70f.toFractionalValue(), clearWarningThreshold = true)
        }

        // Then
        thrown.message shouldBe "warningThreshold and clearWarningThreshold cannot be combined"
    }
}
