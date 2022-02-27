package com.krillsson.sysapi.core.monitoring

import com.google.common.annotations.VisibleForTesting
import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.util.Clock
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

class MonitorMechanism @VisibleForTesting constructor(private val clock: Clock) {
    private var stateChangedAt: OffsetDateTime? = null

    var state = State.INSIDE
    private var eventId: UUID? = null
    private var ongoingEvent: OngoingEvent? = null

    enum class State {
        INSIDE, OUTSIDE_BEFORE_INERTIA, OUTSIDE, INSIDE_BEFORE_INERTIA
    }

    /**
     * Valid state changes
     *
     *
     * Inside -> Inside
     * no action
     *
     *
     * Inside -> Outside before inertia
     * (conditional: outside threshold)
     * save timestamp of state change
     *
     *
     * Outside before inertia -> Outside before inertia
     * no action
     *
     *
     * Outside before inertia -> inside
     * (conditional: inside threshold)
     * reset timestamp
     *
     *
     * Outside before inertia -> outside
     * (conditional: now-timestamp older than inertia)
     * record event
     * reset timestamp (?)
     *
     *
     * Outside -> outside
     * no action
     *
     *
     * Outside -> inside before inertia
     * (conditional: inside threshold)
     * save timestamp of state change
     *
     *
     * Inside before inertia -> inside
     * (conditional: now-timestamp older than inertia)
     * record event
     * reset timestamp (?)
     *
     *
     * Inside before inertia -> Inside before inertia
     * no action
     *
     *
     * Inside before inertia -> outside
     * reset timestamp
     *
     * @return
     */
    fun check(
        monitor: Monitor<MonitoredValue>,
        config: MonitorConfig<out MonitoredValue>,
        value: MonitoredValue,
        outsideThreshold: Boolean
    ): Event? {
        val now = clock.now()
        val pastInertia =
            stateChangedAt != null && Duration.between(stateChangedAt,  /* and */now).compareTo(config.inertia) > 0
        var event: Event? = null
        when (state) {
            State.INSIDE -> {
                if (outsideThreshold) { //Inside -> Outside before inertia
                    stateChangedAt = now
                    state = State.OUTSIDE_BEFORE_INERTIA
                    LOGGER.trace(
                        "{} went outside threshold of {} with {} at {}",
                        config.monitoredItemId,
                        config.threshold,
                        value,
                        now
                    )
                } else {
                    LOGGER.trace(
                        "{} is still inside threshold: {} with {}",
                        config.monitoredItemId,
                        config.threshold,
                        value
                    )
                }
            }
            State.OUTSIDE_BEFORE_INERTIA -> {
                if (outsideThreshold) {
                    if (pastInertia) { //Outside before inertia -> outside
                        LOGGER.debug(
                            "{} have now been outside threshold of {} for more than {}, triggering event...",
                            config.monitoredItemId ?: monitor.type.name,
                            config.threshold,
                            config.inertia
                        )
                        state = State.OUTSIDE
                        stateChangedAt = null
                        eventId = UUID.randomUUID()
                        ongoingEvent = OngoingEvent(
                            eventId!!,
                            monitor.id,
                            config.monitoredItemId,
                            monitor.type,
                            now,
                            config.threshold,
                            value
                        )
                        event = ongoingEvent
                    } else { //Outside before inertia -> Outside before inertia
                        LOGGER.trace(
                            "{} is still outside threshold of {} but inside grace period of {}",
                            config.monitoredItemId,
                            config.threshold,
                            config.inertia
                        )
                    }
                } else { //Outside before inertia -> inside
                    LOGGER.trace(
                        "{} went back inside threshold of {} inside grace period of {}",
                        config.monitoredItemId,
                        config.threshold,
                        config.inertia
                    )
                    stateChangedAt = null
                    state = State.INSIDE
                }
            }
            State.OUTSIDE -> {
                if (outsideThreshold) { //Outside -> outside
                    LOGGER.trace(
                        "{} is still outside threshold of {} at {}",
                        config.monitoredItemId,
                        config.threshold,
                        value
                    )
                } else { //Outside -> Inside before inertia
                    stateChangedAt = now
                    state = State.INSIDE_BEFORE_INERTIA
                    LOGGER.trace("{} went inside threshold of {} at {}", config.monitoredItemId, config.threshold, now)
                }
            }
            State.INSIDE_BEFORE_INERTIA -> {
                if (!outsideThreshold) {
                    if (pastInertia) { //Inside before inertia -> inside
                        LOGGER.debug(
                            "{} have now been inside threshold of {} for more than {}, triggering event...",
                            config.monitoredItemId,
                            config.threshold,
                            config.inertia
                        )
                        state = State.INSIDE
                        stateChangedAt = null
                        event = PastEvent(
                            eventId!!,
                            monitor.id,
                            config.monitoredItemId,
                            now,
                            ongoingEvent!!.startTime,
                            monitor.type,
                            config.threshold,
                            value
                        )
                    } else { //Inside before inertia -> Inside before inertia
                        LOGGER.trace(
                            "{} is still inside threshold of {} with {} but inside grace period of {}",
                            config.monitoredItemId,
                            config.threshold,
                            value,
                            config.inertia
                        )
                    }
                } else { //Inside before inertia -> outside
                    LOGGER.trace(
                        "{} went back outside threshold of {} with {} inside grace period of {}",
                        config.monitoredItemId,
                        config.threshold,
                        value,
                        config.inertia
                    )
                    stateChangedAt = null
                    state = State.OUTSIDE
                }
            }
        }
        return event
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MonitorMechanism::class.java)
    }

}