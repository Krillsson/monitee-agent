package com.krillsson.sysapi.docker

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.docker.State
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ContainerStatisticsHistoryRecorder(
    config: YAMLConfigFile,
    private val containerService: ContainerService,
    private val historyRepository: ContainersHistoryRepository
) {

    private val historyConfiguration = config.metricsConfig.history

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    fun run() {
        val containers = containerService.containers()
        val statistics = containers
            .filter { it.state == State.RUNNING }
            .mapNotNull { container ->
                containerService.statsForContainer(container.id)
            }
        historyRepository.recordContainerStatistics(statistics)
        historyRepository.purgeContainerStatistics(historyConfiguration.purging.olderThan, historyConfiguration.purging.unit)
    }
}