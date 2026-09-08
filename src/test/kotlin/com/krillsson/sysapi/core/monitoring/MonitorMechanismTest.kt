package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.event.EventSeverity
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
    private val warningThreshold = 70L.toNumericalValue()
    private val breachingValue = 95L.toNumericalValue()
    private val warningValue = 75L.toNumericalValue()
    private val recoveredValue = 60L.toNumericalValue()

    private val clock = MutableClock()
    private var monitor = testMonitor(threshold = threshold, inertia = inertia, warningThreshold = warningThreshold)
    private var mechanism = MonitorMechanism(clock)

    private fun breach(value: MonitoredValue = breachingValue): MonitorMechanism.Outcome? =
        mechanism.check(monitor, monitor.config, value, Monitor.Level.CRITICAL)

    private fun warn(value: MonitoredValue = warningValue): MonitorMechanism.Outcome? =
        mechanism.check(monitor, monitor.config, value, Monitor.Level.WARNING)

    private fun recover(value: MonitoredValue = recoveredValue): MonitorMechanism.Outcome? =
        mechanism.check(monitor, monitor.config, value, Monitor.Level.NORMAL)

    private fun raiseOngoingEvent(): OngoingEvent {
        breach()
        clock.advance(inertia.plusSeconds(1))
        return breach()?.event.shouldBeInstanceOf<OngoingEvent>()
    }

    private fun settleAtWarning(): OngoingEvent {
        warn()
        clock.advance(inertia.plusSeconds(1))
        return warn()?.event.shouldBeInstanceOf<OngoingEvent>()
    }

    @Test
    fun `emits nothing while the value stays inside the threshold`() {
        // When
        val outcome = recover()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.NORMAL
        mechanism.pendingLevel shouldBe null
    }

    @Test
    fun `records the first breach without emitting an event`() {
        // When
        val outcome = breach()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.NORMAL
        mechanism.pendingLevel shouldBe Monitor.Level.CRITICAL
    }

    @Test
    fun `emits nothing while the breach is still inside the grace period`() {
        // Given
        breach()

        // When
        clock.advance(Duration.ofMinutes(1))
        val outcome = breach()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.NORMAL
        mechanism.pendingLevel shouldBe Monitor.Level.CRITICAL
    }

    @Test
    fun `never emits an event for a breach that recovers inside the grace period`() {
        // Given
        breach()
        clock.advance(Duration.ofMinutes(1))

        // When
        val outcome = recover()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.NORMAL
        mechanism.pendingLevel shouldBe null
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
        val outcome = breach()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.NORMAL
        mechanism.pendingLevel shouldBe Monitor.Level.CRITICAL
    }

    @Test
    fun `raises an ongoing event once the breach outlasts the inertia`() {
        // Given
        breach()

        // When
        clock.advance(inertia.plusSeconds(1))
        val outcome = breach()

        // Then
        val ongoing = outcome?.event.shouldBeInstanceOf<OngoingEvent>()
        outcome?.notify shouldBe true
        ongoing.monitorId shouldBe MONITOR_ID
        ongoing.monitoredItemId shouldBe MONITORED_ITEM_ID
        ongoing.monitorType shouldBe monitor.type
        ongoing.threshold shouldBe threshold
        ongoing.value shouldBe breachingValue
        ongoing.severity shouldBe EventSeverity.CRITICAL
        ongoing.startTime shouldBe clock.instant()
        mechanism.level shouldBe Monitor.Level.CRITICAL
        mechanism.pendingLevel shouldBe null
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
        pastBoundary?.event.shouldBeInstanceOf<OngoingEvent>()
    }

    @Test
    fun `raises an event on the next check when the inertia is zero`() {
        // Given
        monitor = testMonitor(threshold = threshold, inertia = Duration.ZERO)
        breach()

        // When
        clock.advance(Duration.ofMillis(1))
        val outcome = breach()

        // Then
        outcome?.event.shouldBeInstanceOf<OngoingEvent>()
    }

    @Test
    fun `emits no second event while the breach continues`() {
        // Given
        raiseOngoingEvent()

        // When
        clock.advance(Duration.ofHours(1))
        val outcome = breach()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.CRITICAL
        mechanism.pendingLevel shouldBe null
    }

    @Test
    fun `starts a grace period when the value comes back inside the threshold`() {
        // Given
        raiseOngoingEvent()

        // When
        val outcome = recover()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.CRITICAL
        mechanism.pendingLevel shouldBe Monitor.Level.NORMAL
    }

    @Test
    fun `emits nothing while the recovery is still inside the grace period`() {
        // Given
        raiseOngoingEvent()
        recover()

        // When
        clock.advance(Duration.ofMinutes(1))
        val outcome = recover()

        // Then
        outcome shouldBe null
        mechanism.level shouldBe Monitor.Level.CRITICAL
        mechanism.pendingLevel shouldBe Monitor.Level.NORMAL
    }

    @Test
    fun `closes the incident as a past event carrying the ongoing event's id and start`() {
        // Given
        val ongoing = raiseOngoingEvent()
        recover()

        // When
        clock.advance(inertia.plusSeconds(1))
        val outcome = recover()

        // Then
        val past = outcome?.event.shouldBeInstanceOf<PastEvent>()
        outcome?.notify shouldBe true
        past.id shouldBe ongoing.id
        past.monitorId shouldBe ongoing.monitorId
        past.monitoredItemId shouldBe MONITORED_ITEM_ID
        past.monitorType shouldBe monitor.type
        past.threshold shouldBe threshold
        past.startTime shouldBe ongoing.startTime
        past.startValue shouldBe breachingValue
        past.value shouldBe recoveredValue
        past.severity shouldBe EventSeverity.CRITICAL
        past.endTime shouldBe clock.instant()
        mechanism.level shouldBe Monitor.Level.NORMAL
        mechanism.pendingLevel shouldBe null
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
        mechanism.level shouldBe Monitor.Level.CRITICAL
        mechanism.pendingLevel shouldBe null

        recover()
        clock.advance(inertia.plusSeconds(1))
        recover()?.event.shouldBeInstanceOf<PastEvent>().id shouldBe ongoing.id
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

    @Test
    fun `raises a warning event when the value only reaches the warning level`() {
        // When
        val ongoing = settleAtWarning()

        // Then
        ongoing.severity shouldBe EventSeverity.WARNING
        ongoing.value shouldBe warningValue
        mechanism.level shouldBe Monitor.Level.WARNING
    }

    @Test
    fun `escalates a warning to critical as one event with a second notification`() {
        // Given
        val warning = settleAtWarning()

        // When
        breach()
        clock.advance(inertia.plusSeconds(1))
        val outcome = breach()

        // Then
        val escalated = outcome?.event.shouldBeInstanceOf<OngoingEvent>()
        outcome?.notify shouldBe true
        escalated.id shouldBe warning.id
        escalated.startTime shouldBe warning.startTime
        escalated.severity shouldBe EventSeverity.CRITICAL
        escalated.value shouldBe breachingValue
        mechanism.level shouldBe Monitor.Level.CRITICAL
    }

    @Test
    fun `holds an escalation back until it outlasts the inertia`() {
        // Given
        settleAtWarning()

        // When
        val firstBreach = breach()
        clock.advance(Duration.ofMinutes(1))
        val insideGrace = breach()

        // Then
        firstBreach shouldBe null
        insideGrace shouldBe null
        mechanism.level shouldBe Monitor.Level.WARNING
        mechanism.pendingLevel shouldBe Monitor.Level.CRITICAL
    }

    @Test
    fun `de-escalates a critical event to a warning without notifying`() {
        // Given
        val ongoing = raiseOngoingEvent()

        // When
        warn()
        clock.advance(inertia.plusSeconds(1))
        val outcome = warn()

        // Then
        val deEscalated = outcome?.event.shouldBeInstanceOf<OngoingEvent>()
        outcome?.notify shouldBe false
        deEscalated.id shouldBe ongoing.id
        deEscalated.startTime shouldBe ongoing.startTime
        deEscalated.severity shouldBe EventSeverity.WARNING
        mechanism.level shouldBe Monitor.Level.WARNING
    }

    @Test
    fun `closes a warning-only incident as a warning`() {
        // Given
        settleAtWarning()

        // When
        recover()
        clock.advance(inertia.plusSeconds(1))
        val past = recover()?.event.shouldBeInstanceOf<PastEvent>()

        // Then
        past.severity shouldBe EventSeverity.WARNING
    }

    @Test
    fun `closes an incident that de-escalated at the highest severity it reached`() {
        // Given
        raiseOngoingEvent()
        warn()
        clock.advance(inertia.plusSeconds(1))
        warn()

        // When
        recover()
        clock.advance(inertia.plusSeconds(1))
        val past = recover()?.event.shouldBeInstanceOf<PastEvent>()

        // Then
        past.severity shouldBe EventSeverity.CRITICAL
    }

    @Test
    fun `starts the next incident at the severity it is raised with`() {
        // Given
        raiseOngoingEvent()
        recover()
        clock.advance(inertia.plusSeconds(1))
        recover()

        // When
        val second = settleAtWarning()

        // Then
        second.severity shouldBe EventSeverity.WARNING
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
        val ongoing = breach(values.breaching)?.event.shouldBeInstanceOf<OngoingEvent>()
        recover(values.recovered)
        clock.advance(inertia.plusSeconds(1))
        val past = recover(values.recovered)?.event.shouldBeInstanceOf<PastEvent>()

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
