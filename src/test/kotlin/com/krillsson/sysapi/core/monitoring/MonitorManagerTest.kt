package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.check.CHECK_ID
import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.CheckType
import com.krillsson.sysapi.core.monitoring.event.EventManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test

class MonitorManagerTest {

    private val inertia = Duration.ofMinutes(5)

    private fun manager(
        repository: MonitorRepository,
        eventManager: EventManager = mockk(relaxed = true),
        monitorInputCreator: MonitorInputCreator = mockk(relaxed = true)
    ) = MonitorManager(
        metrics = mockk(relaxed = true),
        eventManager = eventManager,
        repository = repository,
        monitoredItemMissingChecker = mockk(relaxed = true),
        clock = Clock.systemUTC(),
        monitorInputCreator = monitorInputCreator,
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

    private fun inputWithCertificateExpiringIn(days: Long): MonitorInputCreator {
        val result = CheckResult(
            id = UUID.randomUUID(),
            checkId = CHECK_ID,
            checkType = CheckType.HTTP,
            timestamp = Instant.now(),
            successful = true,
            latencyMs = 100,
            message = "OK",
            responseCode = 200,
            errorBody = null,
            resolvedValues = null,
            certificateExpiresAt = Instant.now().plus(Duration.ofDays(days))
        )
        val input = mockk<MonitorInput>()
        every { input.checkCertificateResults } returns listOf(result)
        val creator = mockk<MonitorInputCreator>(relaxed = true)
        every { creator.createInput(any()) } returns input
        return creator
    }

    @Test
    fun `accepts a certificate expiry warning at 30 days with critical at 7`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()
        val written = slot<List<Monitor<MonitoredValue>>>()
        every { repository.write(capture(written)) } returns Unit

        // When
        manager(repository, monitorInputCreator = inputWithCertificateExpiringIn(60)).add(
            inertia,
            Monitor.Type.CHECK_CERTIFICATE_EXPIRY,
            7L.toNumericalValue(),
            CHECK_ID.toString(),
            30L.toNumericalValue()
        )

        // Then
        val monitor = written.captured.single()
        monitor.type shouldBe Monitor.Type.CHECK_CERTIFICATE_EXPIRY
        monitor.config.threshold shouldBe 7L.toNumericalValue()
        monitor.config.warningThreshold shouldBe 30L.toNumericalValue()
    }

    @Test
    fun `rejects a certificate expiry warning that would not trigger before critical`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()

        // When
        val thrown = shouldThrow<IllegalArgumentException> {
            manager(repository, monitorInputCreator = inputWithCertificateExpiringIn(60)).add(
                inertia,
                Monitor.Type.CHECK_CERTIFICATE_EXPIRY,
                7L.toNumericalValue(),
                CHECK_ID.toString(),
                5L.toNumericalValue()
            )
        }

        // Then
        thrown.message shouldBe "Warning threshold NumericalValue(value=5) would trigger no earlier " +
            "than threshold NumericalValue(value=7)"
    }

    @Test
    fun `clears a certificate expiry warning on update and keeps alerting below critical`() {
        // Given
        val repository = mockk<MonitorRepository>(relaxed = true)
        every { repository.read() } returns emptyList()
        val written = slot<List<Monitor<MonitoredValue>>>()
        every { repository.write(capture(written)) } returns Unit
        val manager = manager(repository, monitorInputCreator = inputWithCertificateExpiringIn(60))
        val id = manager.add(
            inertia,
            Monitor.Type.CHECK_CERTIFICATE_EXPIRY,
            7L.toNumericalValue(),
            CHECK_ID.toString(),
            30L.toNumericalValue()
        )

        // When
        manager.update(id, null, null, null, clearWarningThreshold = true)

        // Then
        val monitor = written.captured.single()
        monitor.config.warningThreshold shouldBe null
        monitor.levelFor(20L.toNumericalValue()) shouldBe Monitor.Level.NORMAL
        monitor.levelFor(6L.toNumericalValue()) shouldBe Monitor.Level.CRITICAL
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
