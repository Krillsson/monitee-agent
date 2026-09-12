package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.config.IntervalConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.ups.UpsService
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class MetricSampler(
    private val metrics: Metrics,
    private val containerService: ContainerService,
    private val upsService: UpsService,
    private val repository: MetricSeriesBucketRepository,
    private val taskScheduler: TaskScheduler,
    yamlConfigFile: YAMLConfigFile
) {
    private val logger by logger()
    private val sampling = yamlConfigFile.metricsConfig.history.series.sampling

    @PostConstruct
    fun start() {
        taskScheduler.scheduleAtFixedRate(this::sampleFast, sampling.fast.asDuration())
        taskScheduler.scheduleAtFixedRate(this::sampleSlow, sampling.slow.asDuration())
    }

    fun sampleFast() {
        val now = Instant.now()
        record(
            from("cpu") { SystemLoadMetricExtractor.cpu(metrics.cpuMetrics().cpuLoad(), now) } +
                from("memory") { SystemLoadMetricExtractor.memory(metrics.memoryMetrics().memoryLoad(), now) } +
                from("network") {
                    val networkMetrics = metrics.networkMetrics()
                    SystemLoadMetricExtractor.networkInterfaces(networkMetrics.networkInterfaceLoads(), now) +
                        SystemLoadMetricExtractor.connectivity(networkMetrics.connectivity(), now)
                } +
                from("gpu") { SystemLoadMetricExtractor.gpus(metrics.gpuMetrics().gpuLoads(), now) } +
                from("disk rate") {
                    SystemLoadMetricExtractor.diskRates(metrics.diskMetrics().diskLoadsExcludingSmartData(), now)
                }
        )
    }

    fun sampleSlow() {
        val now = Instant.now()
        record(
            from("filesystem") {
                SystemLoadMetricExtractor.fileSystems(metrics.fileSystemMetrics().fileSystemLoads(), now)
            } +
                from("disk health") {
                    SystemLoadMetricExtractor.diskHealth(metrics.diskMetrics().diskLoads(), now)
                } +
                from("container") { ContainerMetricExtractor.containers(runningContainerMetrics(), now) } +
                from("ups") { UpsMetricExtractor.upsDevices(upsService.upsDevices().map { it.metrics }, now) }
        )
    }

    fun record(samples: List<MetricSample>) {
        if (samples.isEmpty()) {
            return
        }
        repository.saveAll(samples.map { it.asRawBucket() })
    }

    private fun runningContainerMetrics() = containerService.containers()
        .filter { it.state == State.RUNNING }
        .mapNotNull { containerService.statsForContainer(it.id) }

    private fun from(source: String, block: () -> List<MetricSample>): List<MetricSample> =
        runCatching(block).getOrElse { error ->
            logger.warn("Failed to sample {} metrics", source, error)
            emptyList()
        }

    private fun MetricSample.asRawBucket() = MetricSeriesBucketEntity(
        id = UUID.randomUUID(),
        metric = metric,
        itemId = itemId,
        resolution = MetricResolution.RAW,
        bucketStart = timestamp,
        samples = 1,
        minValue = value,
        avgValue = value,
        maxValue = value,
        lastValue = value
    )

    private fun IntervalConfiguration.asDuration(): Duration = Duration.ofSeconds(unit.toSeconds(interval))
}
