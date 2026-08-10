package com.krillsson.sysapi.core.speed

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

abstract class SpeedMeasurementManager(
    private val clock: Clock
) {
    private val speedMeasurementStore = ConcurrentHashMap<String, SpeedMeasurement>()
    private val currentSpeedStore = ConcurrentHashMap<String, CurrentSpeed>()
    private val speedSources = CopyOnWriteArrayList<SpeedSource>()

    fun run() {
        onUpdateStarted()
        for (speedSource in speedSources) {
            val start = speedMeasurementStore[speedSource.name]
            speedSource.update()
            val end = SpeedMeasurement(
                speedSource.currentRead(),
                speedSource.currentWrite(),
                LocalDateTime.now(clock)
            )
            if (start != null) {
                val readPerSecond = measureSpeed(
                    start.sampledAt,
                    end.sampledAt,
                    start.read,
                    end.read
                )
                val writePerSecond = measureSpeed(
                    start.sampledAt,
                    end.sampledAt,
                    start.write,
                    end.write
                )
                LOGGER.trace(
                    "Current speed for {}: read: {}/s write: {}/s",
                    speedSource.name,
                    readPerSecond,
                    writePerSecond
                )
                currentSpeedStore[speedSource.name] = CurrentSpeed(readPerSecond, writePerSecond)
                speedMeasurementStore[speedSource.name] = end
            } else {
                LOGGER.debug("Initializing measurement for {}", speedSource.name)
                speedMeasurementStore[speedSource.name] = end
            }
        }
    }

    open fun onUpdateStarted() {}

    fun register(sources: Collection<SpeedSource>) {
        LOGGER.debug("Registering {}", *sources.stream().map { obj: SpeedSource -> obj.name }
            .toArray())
        speedSources.addAll(sources)
    }

    fun register(speedSource: SpeedSource) {
        LOGGER.debug("Registering {}", speedSource.name)
        speedSources.add(speedSource)
    }

    fun unregister(speedSource: SpeedSource) {
        speedSources.remove(speedSource)
        speedMeasurementStore.remove(speedSource.name)
        currentSpeedStore.remove(speedSource.name)
    }

    fun getCurrentSpeedForName(name: String): Optional<CurrentSpeed> {
        return Optional.ofNullable(currentSpeedStore[name])
    }

    private fun measureSpeed(start: LocalDateTime, end: LocalDateTime, valueStart: Long, valueEnd: Long): Long {
        val duration = Duration.between(start, end).seconds.toDouble()
        val deltaValue = (valueEnd - valueStart).toDouble()
        if (deltaValue <= 0 || duration <= 0) {
            return 0L
        }
        val valuePerSecond = deltaValue / duration
        return valuePerSecond.toLong()
    }

    interface SpeedSource {
        val name: String
        fun update() = Unit
        fun currentRead(): Long
        fun currentWrite(): Long
    }

    class CurrentSpeed @VisibleForTesting constructor(
        @JvmField val readPerSeconds: Long,
        @JvmField val writePerSeconds: Long
    )

    companion object {
        private val LOGGER = LoggerFactory.getLogger(SpeedMeasurementManager::class.java)
    }
}