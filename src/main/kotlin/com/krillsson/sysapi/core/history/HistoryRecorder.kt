package com.krillsson.sysapi.core.history

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.metrics.Metrics
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class HistoryRecorder(
        yamlConfigFile: YAMLConfigFile,
        private val metrics: Metrics,
        private val history: HistoryRepository,
        private val taskScheduler: TaskScheduler
) {

    private val historyConfig = yamlConfigFile.metricsConfig.history

    @PostConstruct
    fun start() {
        val interval = Duration.ofSeconds(historyConfig.unit.toSeconds(historyConfig.interval))
        taskScheduler.scheduleAtFixedRate(this::run, interval)
    }

    fun run() {
        history.record(currentSystemLoad())
        history.purge(historyConfig.purging.olderThan, historyConfig.purging.unit)
    }

    private fun currentSystemLoad(): HistorySystemLoad {
        val cpuMetrics = metrics.cpuMetrics()
        val networkMetrics = metrics.networkMetrics()
        val cpuLoad = cpuMetrics.cpuLoad()
        return HistorySystemLoad(
                cpuMetrics.uptime(),
                cpuLoad.systemLoadAverage,
                cpuLoad,
                networkMetrics.networkInterfaceLoads(),
                networkMetrics.connectivity(),
                metrics.diskMetrics().diskLoads(),
                metrics.fileSystemMetrics().fileSystemLoads(),
                metrics.memoryMetrics().memoryLoad(),
                metrics.gpuMetrics().gpuLoads(),
                metrics.motherboardMetrics().motherboardHealth()
        )
    }
}