package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.core.domain.gpu.GpuHealth
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.round
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import oshi.hardware.GpuStats
import oshi.hardware.GpuTicks
import oshi.hardware.HardwareAbstractionLayer
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.TimeUnit

@Component
class DefaultGpuLoadMetrics(private val hal: HardwareAbstractionLayer) {

    val logger by logger()

    private val gpuLoadMetric = Sinks.many()
        .replay()
        .latest<List<GpuLoad>>()

    private val sessions = mutableMapOf<String, GpuStats>()
    private val previousTicks = mutableMapOf<String, GpuTicks>()
    private val previousSampledAt = mutableMapOf<String, Long>()
    private val names = mutableMapOf<String, String>()
    private val vramTotals = mutableMapOf<String, Long>()

    @Volatile
    var gpuLoads: List<GpuLoad> = emptyList()

    init {
        refreshSessions()
        sessions.forEach { (deviceId, stats) ->
            runCatching { previousTicks[deviceId] = stats.gpuTicks }
        }
        gpuLoads = sample()
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    fun run() {
        gpuLoads = sample()
        gpuLoadMetric.tryEmitNext(gpuLoads)
    }

    fun gpuLoadEvents(): Flux<List<GpuLoad>> {
        return gpuLoadMetric.asFlux()
    }

    fun gpuLoadEventsById(id: String): Flux<GpuLoad> {
        return gpuLoadMetric.asFlux()
            .mapNotNull { list -> list.firstOrNull { it.id == id } }
    }

    fun gpuLoadById(id: String): GpuLoad? {
        return gpuLoads.firstOrNull { it.id == id }
    }

    private fun sample(): List<GpuLoad> {
        refreshSessions()
        return sessions.map { (deviceId, stats) ->
            GpuLoad(
                id = deviceId,
                name = names[deviceId].orEmpty(),
                coreLoad = utilization(deviceId, stats, System.nanoTime()),
                vramUsedBytes = stats.vramUsed,
                vramTotalBytes = vramTotals[deviceId] ?: -1L,
                health = GpuHealth(
                    temperature = stats.temperature,
                    fanPercent = stats.fanSpeedPercent,
                    powerDraw = stats.powerDraw,
                    coreClockMhz = stats.coreClockMhz,
                    memoryClockMhz = stats.memoryClockMhz
                )
            )
        }
    }

    private fun utilization(deviceId: String, stats: GpuStats, nowNanos: Long): Double {
        val current = stats.gpuTicks
        val previous = previousTicks.put(deviceId, current)
        val previousAt = previousSampledAt.put(deviceId, nowNanos)
        if (previous == null || previousAt == null) return fallback(stats)

        val deltaActive = current.activeTicks - previous.activeTicks
        val deltaIdle = current.idleTicks - previous.idleTicks
        if (deltaActive < 0 || deltaIdle < 0) return fallback(stats)  // counter reset / instance churn

        // Platforms that report real idle ticks (macOS Apple Silicon)
        if (deltaIdle > 0) {
            return (deltaActive * 100.0 / (deltaActive + deltaIdle)).coerceIn(0.0, 100.0).round(2)
        }
        // Windows: idle is always 0; activeTicks are 100ns units, so use elapsed wall clock
        val elapsed100ns = (nowNanos - previousAt) / 100
        if (elapsed100ns <= 0 || (current.activeTicks == 0L && previous.activeTicks == 0L)) return fallback(stats)
        return (deltaActive * 100.0 / elapsed100ns).coerceIn(0.0, 100.0).round(2)
    }

    private fun fallback(stats: GpuStats) = stats.gpuUtilization.takeIf { it >= 0 } ?: 0.0

    private fun refreshSessions() {
        val cards = hal.graphicsCards
        val currentIds = cards.map { it.deviceId }.toSet()
        cards.forEach { card ->
            val deviceId = card.deviceId
            names[deviceId] = card.name
            vramTotals[deviceId] = card.vRam
            if (!sessions.containsKey(deviceId)) {
                runCatching { sessions[deviceId] = card.createStatsSession() }
                    .onFailure { logger.warn("Unable to open GPU stats session for {}", deviceId, it) }
            }
        }
        (sessions.keys - currentIds).forEach { deviceId ->
            runCatching { sessions[deviceId]?.close() }
            sessions.remove(deviceId)
            previousTicks.remove(deviceId)
        }
    }

    @PreDestroy
    fun close() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
    }
}
