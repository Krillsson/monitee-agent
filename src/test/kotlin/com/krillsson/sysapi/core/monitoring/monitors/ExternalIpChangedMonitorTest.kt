package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMechanism
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.MutableClock
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.util.UUID

class ExternalIpChangedMonitorTest {

    private val clock = MutableClock()

    private fun monitor(inertia: Duration = Duration.ofMinutes(10)) = ExternalIpChangedMonitor(
        id = UUID.randomUUID(),
        config = MonitorConfig(
            monitoredItemId = null,
            threshold = MonitoredValue.ConditionalValue(true),
            inertia = inertia
        ),
        clock = clock
    )

    private fun input(externalIp: String?, previousExternalIp: String?): MonitorInput {
        val input = mockk<MonitorInput>()
        every { input.load.connectivity } returns Connectivity(
            externalIp = externalIp,
            previousExternalIp = previousExternalIp,
            localIp = "192.168.1.2",
            connected = externalIp != null
        )
        return input
    }

    @Test
    fun `reports no change while the external IP stays the same`() {
        // Given
        val monitor = monitor()

        // When
        val value = monitor.selectValue(input("1.2.3.4", "1.2.3.4"))

        // Then
        value shouldBe MonitoredValue.ConditionalValue(true)
    }

    @Test
    fun `reports no change while the external IP is unknown`() {
        // Given
        val monitor = monitor()

        // When
        val value = monitor.selectValue(input(null, "1.2.3.4"))

        // Then
        value shouldBe MonitoredValue.ConditionalValue(true)
    }

    @Test
    fun `keeps reporting a change after the connectivity check has moved on`() {
        // Given
        val monitor = monitor()
        monitor.selectValue(input("1.2.3.4", "1.2.3.4"))
        monitor.selectValue(input("5.6.7.8", "1.2.3.4"))

        // When
        clock.advance(Duration.ofMinutes(6))
        val value = monitor.selectValue(input("5.6.7.8", "5.6.7.8"))

        // Then
        value shouldBe MonitoredValue.ConditionalValue(false)
    }

    @Test
    fun `reports a change that happened before the monitor first looked`() {
        // Given
        val monitor = monitor()

        // When
        val value = monitor.selectValue(input("5.6.7.8", "1.2.3.4"))

        // Then
        value shouldBe MonitoredValue.ConditionalValue(false)
    }

    @Test
    fun `settles on the new external IP once the change has outlasted the inertia`() {
        // Given
        val monitor = monitor(Duration.ofMinutes(10))
        monitor.selectValue(input("5.6.7.8", "1.2.3.4"))

        // When
        clock.advance(Duration.ofMinutes(10).plus(ExternalIpChangedMonitor.holdChangeBeyondInertia).plusSeconds(1))
        val value = monitor.selectValue(input("5.6.7.8", "5.6.7.8"))

        // Then
        value shouldBe MonitoredValue.ConditionalValue(true)
    }

    @Test
    fun `reports no change when the external IP goes back before the change was reported`() {
        // Given
        val monitor = monitor()
        monitor.selectValue(input("1.2.3.4", "1.2.3.4"))
        monitor.selectValue(input("5.6.7.8", "1.2.3.4"))

        // When
        clock.advance(Duration.ofMinutes(5))
        val value = monitor.selectValue(input("1.2.3.4", "5.6.7.8"))

        // Then
        value shouldBe MonitoredValue.ConditionalValue(true)
    }

    @ParameterizedTest
    @ValueSource(longs = [0, 1, 5, 10, 60])
    fun `raises one event for a change and resolves it again whatever the inertia`(inertiaMinutes: Long) {
        // Given
        val monitor = monitor(Duration.ofMinutes(inertiaMinutes))
        val mechanism = MonitorMechanism(clock)
        val connectivityCheckEvery = Duration.ofMinutes(5)
        val monitorEvery = Duration.ofSeconds(15)
        var previousIp = "1.2.3.4"
        var currentIp = "1.2.3.4"
        var sinceConnectivityCheck = Duration.ZERO
        val outcomes = mutableListOf<MonitorMechanism.Outcome>()

        // When
        repeat((Duration.ofMinutes(3 * inertiaMinutes + 20).seconds / monitorEvery.seconds).toInt()) { tick ->
            if (sinceConnectivityCheck >= connectivityCheckEvery || tick == 0) {
                previousIp = currentIp
                currentIp = if (tick == 0) "1.2.3.4" else "5.6.7.8"
                sinceConnectivityCheck = Duration.ZERO
            }
            val value = monitor.selectValue(input(currentIp, previousIp))
            mechanism.check(monitor, monitor.config, value, monitor.levelFor(value))?.let { outcomes += it }
            clock.advance(monitorEvery)
            sinceConnectivityCheck = sinceConnectivityCheck.plus(monitorEvery)
        }

        // Then
        outcomes shouldHaveSize 2
        outcomes[0].event.shouldBeInstanceOf<OngoingEvent>()
        outcomes[1].event.shouldBeInstanceOf<PastEvent>()
        mechanism.level shouldBe Monitor.Level.NORMAL
    }
}
