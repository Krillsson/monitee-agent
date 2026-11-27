package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.Threads
import com.krillsson.sysapi.core.domain.memory.MemoryInfo
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.metrics.MemoryMetrics
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import oshi.hardware.GlobalMemory
import oshi.hardware.HardwareAbstractionLayer
import oshi.hardware.PhysicalMemory
import oshi.software.os.OperatingSystem
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.TimeUnit

@Component
class DefaultMemoryMetrics(
    private val hal: HardwareAbstractionLayer,
    private val operatingSystem: OperatingSystem
) : MemoryMetrics {

    private val memoryMetric = Sinks.many()
        .replay()
        .latest<MemoryLoad>()

    override fun memoryLoad(): MemoryLoad {
        val memory = hal.memory
        val virtualMemory = memory.virtualMemory
        return MemoryLoad(
            operatingSystem.processCount,
            virtualMemory.swapTotal,
            virtualMemory.swapUsed,
            memory.total,
            memory.available,
            usedPercent(memory).toDouble()
        )
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS, scheduler = Threads.SAMPLING_SCHEDULER)
    fun runMeasurement() {
        memoryMetric.tryEmitNext(memoryLoad())
    }

    override fun memoryLoadEvents(): Flux<MemoryLoad> {
        return memoryMetric.asFlux()
    }

    override fun memoryInfo(): MemoryInfo {
        val memory = hal.memory
        val virtualMemory = memory.virtualMemory
        return MemoryInfo(
            virtualMemory.swapTotal,
            memory.total,
            memory.physicalMemory.map {
                it.asPhysicalMemory()
            }
        )
    }

    private fun PhysicalMemory.asPhysicalMemory() =
        com.krillsson.sysapi.core.domain.memory.PhysicalMemory(
            bankLabel,
            capacity,
            clockSpeed,
            manufacturer, memoryType
        )

    private fun usedPercent(memory: GlobalMemory): Int {
        val free = memory.available
        val total = memory.total
        val used = total - free
        return (used * 100.0 / total + 0.5).toInt()
    }
}