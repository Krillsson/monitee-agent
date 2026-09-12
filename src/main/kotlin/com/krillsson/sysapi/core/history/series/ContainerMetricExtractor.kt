package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import java.time.Instant

object ContainerMetricExtractor {

    fun containers(metrics: List<ContainerMetrics>, timestamp: Instant): List<MetricSample> =
        metrics.flatMap { container ->
            listOf(
                sample(MetricId.CONTAINER_RUNNING, container.id, timestamp, true.asFlag()),
                sample(
                    MetricId.CONTAINER_CPU_PERCENT,
                    container.id,
                    timestamp,
                    container.cpuUsage.usagePercentTotal
                ),
                sample(
                    MetricId.CONTAINER_MEMORY_USED_BYTES,
                    container.id,
                    timestamp,
                    container.memoryUsage.usageBytes.toDouble()
                ),
                sample(
                    MetricId.CONTAINER_MEMORY_LIMIT_BYTES,
                    container.id,
                    timestamp,
                    container.memoryUsage.limitBytes.toDouble()
                ),
                sample(
                    MetricId.CONTAINER_MEMORY_USED_PERCENT,
                    container.id,
                    timestamp,
                    container.memoryUsage.usagePercent
                ),
                sample(
                    MetricId.CONTAINER_RX_BYTES,
                    container.id,
                    timestamp,
                    container.networkUsage.bytesReceived.toDouble()
                ),
                sample(
                    MetricId.CONTAINER_TX_BYTES,
                    container.id,
                    timestamp,
                    container.networkUsage.bytesTransferred.toDouble()
                ),
                sample(
                    MetricId.CONTAINER_BLOCK_READ_BYTES,
                    container.id,
                    timestamp,
                    container.blockIOUsage.bytesRead.toDouble()
                ),
                sample(
                    MetricId.CONTAINER_BLOCK_WRITE_BYTES,
                    container.id,
                    timestamp,
                    container.blockIOUsage.bytesWritten.toDouble()
                )
            )
        }
}
