package com.krillsson.sysapi.core.monitoring

import com.google.common.annotations.VisibleForTesting
import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

class MonitorMechanism @VisibleForTesting constructor(private val clock: Clock) {
    private var stateChangedAt: Instant? = null
    private var ongoingEvent: OngoingEvent? = null
    private var highestLevel = Monitor.Level.NORMAL

    var level = Monitor.Level.NORMAL
        private set

    var pendingLevel: Monitor.Level? = null
        private set

    data class Outcome(val event: Event, val notify: Boolean)

    /**
     * Valid state changes
     *
     *
     * Level -> same level
     * cancel any pending change
     *
     *
     * Level -> a different level, first observation
     * save timestamp of state change
     *
     *
     * Level -> the same pending level, inside the grace period
     * no action
     *
     *
     * Normal -> warning or critical
     * (conditional: now-timestamp older than inertia)
     * record an ongoing event and notify
     *
     *
     * Warning -> critical
     * (conditional: now-timestamp older than inertia)
     * raise the severity of the ongoing event, keeping its id, and notify
     *
     *
     * Critical -> warning
     * (conditional: now-timestamp older than inertia)
     * lower the severity of the ongoing event, keeping its id, without notifying
     *
     *
     * Warning or critical -> normal
     * (conditional: now-timestamp older than inertia)
     * close the ongoing event as a past event carrying the highest severity it reached
     *
     * @return
     */
    fun check(
        monitor: Monitor<MonitoredValue>,
        config: MonitorConfig<out MonitoredValue>,
        value: MonitoredValue,
        observedLevel: Monitor.Level
    ): Outcome? {
        val now = clock.instant()
        if (observedLevel == level) {
            if (pendingLevel != null) {
                LOGGER.trace(
                    "{} settled back at {} of {} inside grace period of {}",
                    config.monitoredItemId,
                    level,
                    config.threshold,
                    config.inertia
                )
                pendingLevel = null
                stateChangedAt = null
            }
            return null
        }
        if (observedLevel != pendingLevel) {
            LOGGER.trace(
                "{} went from {} to {} with {} at {}",
                config.monitoredItemId,
                level,
                observedLevel,
                value,
                now
            )
            pendingLevel = observedLevel
            stateChangedAt = now
            return null
        }
        if (Duration.between(stateChangedAt, now).compareTo(config.inertia) <= 0) {
            LOGGER.trace(
                "{} is still at {} but inside grace period of {}",
                config.monitoredItemId,
                observedLevel,
                config.inertia
            )
            return null
        }
        LOGGER.info(
            "{}:{} have now been at {} of {} for more than {}, triggering event...",
            monitor.type.name,
            config.monitoredItemId,
            observedLevel,
            config.threshold,
            config.inertia
        )
        val previousLevel = level
        val previous = ongoingEvent
        level = observedLevel
        pendingLevel = null
        stateChangedAt = null
        return when {
            previous == null -> raise(monitor, config, value, now, observedLevel)
            observedLevel == Monitor.Level.NORMAL -> resolve(monitor, config, value, now, previous)
            else -> reclassify(config, value, observedLevel, previousLevel, previous)
        }
    }

    private fun raise(
        monitor: Monitor<MonitoredValue>,
        config: MonitorConfig<out MonitoredValue>,
        value: MonitoredValue,
        now: Instant,
        observedLevel: Monitor.Level
    ): Outcome {
        highestLevel = observedLevel
        val event = OngoingEvent(
            id = UUID.randomUUID(),
            monitorId = monitor.id,
            monitoredItemId = config.monitoredItemId,
            monitorType = monitor.type,
            startTime = now,
            threshold = config.threshold,
            value = value,
            severity = observedLevel.asSeverity()
        )
        ongoingEvent = event
        return Outcome(event, notify = true)
    }

    private fun reclassify(
        config: MonitorConfig<out MonitoredValue>,
        value: MonitoredValue,
        observedLevel: Monitor.Level,
        previousLevel: Monitor.Level,
        previous: OngoingEvent
    ): Outcome {
        val escalating = observedLevel > previousLevel
        if (escalating) {
            highestLevel = observedLevel
        }
        val event = OngoingEvent(
            id = previous.id,
            monitorId = previous.monitorId,
            monitoredItemId = config.monitoredItemId,
            monitorType = previous.monitorType,
            startTime = previous.startTime,
            threshold = config.threshold,
            value = value,
            severity = observedLevel.asSeverity()
        )
        ongoingEvent = event
        return Outcome(event, notify = escalating)
    }

    private fun resolve(
        monitor: Monitor<MonitoredValue>,
        config: MonitorConfig<out MonitoredValue>,
        value: MonitoredValue,
        now: Instant,
        previous: OngoingEvent
    ): Outcome {
        val event = PastEvent(
            id = previous.id,
            monitorId = monitor.id,
            monitoredItemId = config.monitoredItemId,
            startTime = previous.startTime,
            endTime = now,
            type = monitor.type,
            threshold = config.threshold,
            endValue = value,
            startValue = previous.value,
            severity = highestLevel.asSeverity()
        )
        ongoingEvent = null
        highestLevel = Monitor.Level.NORMAL
        return Outcome(event, notify = true)
    }

    private fun Monitor.Level.asSeverity() =
        if (this == Monitor.Level.WARNING) EventSeverity.WARNING else EventSeverity.CRITICAL

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MonitorMechanism::class.java)
    }

}
