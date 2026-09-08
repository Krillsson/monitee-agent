package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class MonitorMechanismTest {

    private val inertia = Duration.ofMinutes(5)
    private val threshold = 80L.toNumericalValue()
    private val breachingValue = 95L.toNumericalValue()
    private val recoveredValue = 60L.toNumericalValue()

    private val clock = MutableClock()
    private var monitor = testMonitor(threshold = threshold, inertia = inertia)
    private var mechanism = MonitorMechanism(clock)

    private fun breach(value: MonitoredValue = breachingValue): Event? =
        mechanism.check(monitor, monitor.config, value, true)

    private fun recover(value: MonitoredValue = recoveredValue): Event? =
        mechanism.check(monitor, monitor.config, value, false)

    private fun raiseOngoingEvent(): OngoingEvent {
        breach()
        clock.advance(inertia.plusSeconds(1))
        return breach().shouldBeInstanceOf<OngoingEvent>()
    }

    @Test
    fun `emits nothing while the value stays inside the threshold`() {
        // When
        val event = recover()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.INSIDE
    }

    @Test
    fun `records the first breach without emitting an event`() {
        // When
        val event = breach()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.OUTSIDE_BEFORE_INERTIA
    }

    @Test
    fun `emits nothing while the breach is still inside the grace period`() {
        // Given
        breach()

        // When
        clock.advance(Duration.ofMinutes(1))
        val event = breach()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.OUTSIDE_BEFORE_INERTIA
    }

    @Test
    fun `never emits an event for a breach that recovers inside the grace period`() {
        // Given
        breach()
        clock.advance(Duration.ofMinutes(1))

        // When
        val event = recover()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.INSIDE
    }

    @Test
    fun `restarts the grace period after a breach that recovered inside it`() {
        // Given
        breach()
        clock.advance(Duration.ofMinutes(1))
        recover()

        // When
        breach()
        clock.advance(inertia)
        val event = breach()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.OUTSIDE_BEFORE_INERTIA
    }

    @Test
    fun `raises an ongoing event once the breach outlasts the inertia`() {
        // Given
        breach()

        // When
        clock.advance(inertia.plusSeconds(1))
        val event = breach()

        // Then
        val ongoing = event.shouldBeInstanceOf<OngoingEvent>()
        ongoing.monitorId shouldBe MONITOR_ID
        ongoing.monitoredItemId shouldBe MONITORED_ITEM_ID
        ongoing.monitorType shouldBe monitor.type
        ongoing.threshold shouldBe threshold
        ongoing.value shouldBe breachingValue
        ongoing.startTime shouldBe clock.instant()
        mechanism.state shouldBe MonitorMechanism.State.OUTSIDE
    }

    @Test
    fun `does not raise an event exactly at the inertia boundary but does one tick later`() {
        // Given
        breach()

        // When
        clock.advance(inertia)
        val atBoundary = breach()
        clock.advance(Duration.ofMillis(1))
        val pastBoundary = breach()

        // Then
        atBoundary shouldBe null
        pastBoundary.shouldBeInstanceOf<OngoingEvent>()
    }

    @Test
    fun `raises an event on the next check when the inertia is zero`() {
        // Given
        monitor = testMonitor(threshold = threshold, inertia = Duration.ZERO)
        breach()

        // When
        clock.advance(Duration.ofMillis(1))
        val event = breach()

        // Then
        event.shouldBeInstanceOf<OngoingEvent>()
    }

    @Test
    fun `emits no second event while the breach continues`() {
        // Given
        raiseOngoingEvent()

        // When
        clock.advance(Duration.ofHours(1))
        val event = breach()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.OUTSIDE
    }

    @Test
    fun `starts a grace period when the value comes back inside the threshold`() {
        // Given
        raiseOngoingEvent()

        // When
        val event = recover()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.INSIDE_BEFORE_INERTIA
    }

    @Test
    fun `emits nothing while the recovery is still inside the grace period`() {
        // Given
        raiseOngoingEvent()
        recover()

        // When
        clock.advance(Duration.ofMinutes(1))
        val event = recover()

        // Then
        event shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.INSIDE_BEFORE_INERTIA
    }

    @Test
    fun `closes the incident as a past event carrying the ongoing event's id and start`() {
        // Given
        val ongoing = raiseOngoingEvent()
        recover()

        // When
        clock.advance(inertia.plusSeconds(1))
        val event = recover()

        // Then
        val past = event.shouldBeInstanceOf<PastEvent>()
        past.id shouldBe ongoing.id
        past.monitorId shouldBe ongoing.monitorId
        past.monitoredItemId shouldBe MONITORED_ITEM_ID
        past.monitorType shouldBe monitor.type
        past.threshold shouldBe threshold
        past.startTime shouldBe ongoing.startTime
        past.startValue shouldBe breachingValue
        past.value shouldBe recoveredValue
        past.endTime shouldBe clock.instant()
        mechanism.state shouldBe MonitorMechanism.State.INSIDE
    }

    @Test
    fun `does not open a second event when the value breaches again during the recovery grace period`() {
        // Given
        val ongoing = raiseOngoingEvent()
        recover()
        clock.advance(Duration.ofMinutes(1))

        // When
        val duringRecovery = breach()
        clock.advance(Duration.ofHours(1))
        val stillBreaching = breach()

        // Then
        duringRecovery shouldBe null
        stillBreaching shouldBe null
        mechanism.state shouldBe MonitorMechanism.State.OUTSIDE

        recover()
        clock.advance(inertia.plusSeconds(1))
        recover().shouldBeInstanceOf<PastEvent>().id shouldBe ongoing.id
    }

    @Test
    fun `a breach that recovers and breaches again produces two distinct event ids`() {
        // Given
        val first = raiseOngoingEvent()
        recover()
        clock.advance(inertia.plusSeconds(1))
        recover()

        // When
        val second = raiseOngoingEvent()

        // Then
        first.id shouldNotBe second.id
    }

    @ParameterizedTest
    @EnumSource(Monitor.ValueType::class)
    fun `carries the monitored value through a full breach and recovery for every value kind`(valueType: Monitor.ValueType) {
        // Given
        val values = valuesFor(valueType)
        monitor = testMonitor(threshold = values.threshold, inertia = inertia, type = values.monitorType)

        // When
        breach(values.breaching)
        clock.advance(inertia.plusSeconds(1))
        val ongoing = breach(values.breaching).shouldBeInstanceOf<OngoingEvent>()
        recover(values.recovered)
        clock.advance(inertia.plusSeconds(1))
        val past = recover(values.recovered).shouldBeInstanceOf<PastEvent>()

        // Then
        ongoing.monitorType shouldBe values.monitorType
        ongoing.threshold shouldBe values.threshold
        ongoing.value shouldBe values.breaching
        past.id shouldBe ongoing.id
        past.threshold shouldBe values.threshold
        past.startValue shouldBe values.breaching
        past.value shouldBe values.recovered
    }
}
