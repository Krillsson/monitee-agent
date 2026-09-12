package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.core.domain.docker.BlockIOUsage
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.docker.CpuUsage
import com.krillsson.sysapi.core.domain.docker.MemoryUsage
import com.krillsson.sysapi.core.domain.docker.NetworkUsage
import com.krillsson.sysapi.core.domain.docker.ThrottlingData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ContainerMetricExtractorTest {

    private val timestamp = at("2026-09-12T10:00:00Z")

    @Test
    fun `marks every container it is given as running`() {
        // Given
        val metrics = listOf(containerMetrics(id = "abc"), containerMetrics(id = "def"))

        // When
        val samples = ContainerMetricExtractor.containers(metrics, timestamp)

        // Then
        samples.valueOf(MetricId.CONTAINER_RUNNING, "abc") shouldBe 1.0
        samples.valueOf(MetricId.CONTAINER_RUNNING, "def") shouldBe 1.0
    }

    @Test
    fun `keeps cpu and memory gauges apart from the traffic counters`() {
        // Given
        val metrics = listOf(
            containerMetrics(
                id = "abc",
                usagePercentTotal = 12.5,
                usageBytes = 700,
                bytesReceived = 4096,
                bytesWritten = 8192
            )
        )

        // When
        val samples = ContainerMetricExtractor.containers(metrics, timestamp)

        // Then
        samples.valueOf(MetricId.CONTAINER_CPU_PERCENT, "abc") shouldBe 12.5
        samples.valueOf(MetricId.CONTAINER_MEMORY_USED_BYTES, "abc") shouldBe 700.0
        samples.valueOf(MetricId.CONTAINER_RX_BYTES, "abc") shouldBe 4096.0
        samples.valueOf(MetricId.CONTAINER_BLOCK_WRITE_BYTES, "abc") shouldBe 8192.0
        MetricId.CONTAINER_CPU_PERCENT.kind shouldBe MetricKind.GAUGE
        MetricId.CONTAINER_RX_BYTES.kind shouldBe MetricKind.COUNTER
    }

    @Test
    fun `records nothing when no container is running`() {
        // Given
        val metrics = emptyList<ContainerMetrics>()

        // When
        val samples = ContainerMetricExtractor.containers(metrics, timestamp)

        // Then
        samples.shouldBeEmpty()
    }

    private fun containerMetrics(
        id: String,
        usagePercentTotal: Double = 5.0,
        usageBytes: Long = 100,
        bytesReceived: Long = 200,
        bytesWritten: Long = 300
    ) = ContainerMetrics(
        id = id,
        cpuUsage = CpuUsage(1.0, usagePercentTotal, ThrottlingData(0, 0, 0)),
        memoryUsage = MemoryUsage(usageBytes, 10.0, 1000),
        currentPid = 1,
        networkUsage = NetworkUsage(bytesReceived, 400),
        blockIOUsage = BlockIOUsage(bytesWritten, 500)
    )
}
