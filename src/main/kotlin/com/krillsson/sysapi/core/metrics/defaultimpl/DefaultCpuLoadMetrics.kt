package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.Threads
import com.krillsson.sysapi.core.domain.cpu.CoreLoad
import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.cpu.LoadAverages
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.round
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import oshi.hardware.CentralProcessor
import oshi.hardware.CentralProcessor.TickType
import oshi.software.os.OperatingSystem
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.TimeUnit

@Component
class DefaultCpuLoadMetrics(
    private val processor: CentralProcessor,
    private val operatingSystem: OperatingSystem,
    @Qualifier("defaultCpuSensors") private val cpuSensors: DefaultCpuSensors,
) {

    val logger by logger()

    private val cpuLoadMetric = Sinks.many()
        .replay()
        .latest<CpuLoad>()

    private var oldTicks: LongArray = LongArray(TickType.values().size)
    private var oldProcTicks: Array<LongArray> = Array(processor.logicalProcessorCount) {
        LongArray(
            TickType.values().size
        )
    }

    var coreLoads = updateCoreLoads()
    var systemUsage = updateSystemUsagePercent()
    var loadAverages = updateLoadAverage()

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.SECONDS, scheduler = Threads.SAMPLING_SCHEDULER)
    fun run() {
        coreLoads = updateCoreLoads()
        systemUsage = updateSystemUsagePercent()
        loadAverages = updateLoadAverage()
        cpuLoadMetric.tryEmitNext(createCpuLoad())
    }

    fun createCpuLoad(): CpuLoad {
        return CpuLoad(
            systemUsage,
            (loadAverages.oneMinute * 100.0).round(2),
            loadAverages,
            coreLoads,
            cpuSensors.cpuHealth(),
            operatingSystem.processCount,
            operatingSystem.threadCount
        )
    }

    fun cpuLoadEvents(): Flux<CpuLoad> {
        return cpuLoadMetric.asFlux()
    }

    private fun updateSystemUsagePercent(): Double {
        return (100.0 * processorTicks()).round(2)
    }

    private fun updateCoreLoads(): List<CoreLoad> {
        val ticks = coreTicks()
        return ticks.map {
            CoreLoad((100.0 * it).round(2))
        }
    }

    private fun updateLoadAverage(): LoadAverages {
        val systemLoadAverage = processor.getSystemLoadAverage(3)
        return LoadAverages(systemLoadAverage[0], systemLoadAverage[1], systemLoadAverage[2])
    }

    private fun processorTicks(): Double {
        val d = processor.getSystemCpuLoadBetweenTicks(oldTicks)
        oldTicks = processor.systemCpuLoadTicks
        return d
    }

    private fun coreTicks(): DoubleArray {
        val p = processor.getProcessorCpuLoadBetweenTicks(oldProcTicks)
        oldProcTicks = processor.processorCpuLoadTicks
        return p
    }
}