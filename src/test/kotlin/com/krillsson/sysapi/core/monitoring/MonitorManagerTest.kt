package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.monitoring.event.EventManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.util.UUID
import org.junit.jupiter.api.Test

class MonitorManagerTest {

    @Test
    fun `purges events left behind by monitors that were not restored from disk`() {
        // Given
        val restoredMonitorId = UUID.randomUUID()
        val restoredMonitor = mockk<Monitor<MonitoredValue>>(relaxed = true)
        every { restoredMonitor.id } returns restoredMonitorId

        val eventManager = mockk<EventManager>(relaxed = true)
        val repository = mockk<MonitorRepository>()
        every { repository.read() } returns listOf(restoredMonitor)

        val manager = MonitorManager(
            metrics = mockk(relaxed = true),
            eventManager = eventManager,
            repository = repository,
            monitoredItemMissingChecker = mockk(relaxed = true),
            clock = Clock.systemUTC(),
            monitorInputCreator = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            monitorFactory = mockk(relaxed = true)
        )

        // When
        manager.start()

        // Then
        verify { eventManager.removeEventsForMonitorsNotIn(setOf(restoredMonitorId)) }
    }
}
