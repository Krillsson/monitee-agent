package com.krillsson.sysapi.ups

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.docker.ContainersHistoryRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit


@Component
class UpsMetricsHistoryRecorder(
    config: YAMLConfigFile,
    private val upsService: UpsService,
    private val historyRepository: UpsMetricsHistoryRepository
) {

    private val historyConfiguration = config.metricsConfig.history

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    fun run() {
        val devices = upsService.upsDevices()
        val metrics = devices.map { it.metrics }
        historyRepository.recordUpsMetrics(metrics)
        historyRepository.purgeUpsMetrics(historyConfiguration.purging.olderThan, historyConfiguration.purging.unit)
    }
}