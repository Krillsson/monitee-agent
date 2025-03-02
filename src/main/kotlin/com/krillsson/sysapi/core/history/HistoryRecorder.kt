package com.krillsson.sysapi.core.history

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.metrics.Metrics
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class HistoryRecorder(
        yamlConfigFile: YAMLConfigFile,
        private val metrics: Metrics,
        private val history: HistoryRepository
) {

    private val historyConfig = yamlConfigFile.metricsConfig.history

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    fun run() {
        val metrics = metrics.systemLoad(ProcessSort.MEMORY, -1)
        history.record(metrics.asHistorySystemLoad())
        history.purge(historyConfig.purging.olderThan, historyConfig.purging.unit)
    }

    private fun com.krillsson.sysapi.core.domain.system.SystemLoad.asHistorySystemLoad(): HistorySystemLoad {
        return HistorySystemLoad(
                uptime,
                systemLoadAverage,
                cpuLoad,
                networkInterfaceLoads,
                connectivity,
                diskLoads,
                fileSystemLoads,
                memory,
                gpuLoads,
                motherboardHealth
        )
    }
}