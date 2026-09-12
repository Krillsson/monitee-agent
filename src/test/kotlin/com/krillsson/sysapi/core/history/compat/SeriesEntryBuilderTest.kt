package com.krillsson.sysapi.core.history.compat

import com.krillsson.sysapi.core.history.series.MetricId
import com.krillsson.sysapi.core.history.series.MetricResolution
import com.krillsson.sysapi.core.history.series.at
import com.krillsson.sysapi.core.history.series.seriesBucket
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SeriesEntryBuilderTest {

    private val timestamp = at("2026-09-12T10:00:00Z")

    @Test
    fun `takes a gauge from its average and a counter from its last value`() {
        // Given
        val snapshot = snapshotOf(
            bucket(MetricId.CPU_USAGE_PERCENT, avgValue = 42.0, lastValue = 99.0),
            bucket(MetricId.NETWORK_RX_BYTES, itemId = "eth0", avgValue = 500.0, lastValue = 1000.0)
        )

        // When
        val point = SeriesEntryBuilder.systemPoint(timestamp, snapshot)

        // Then
        point.processorMetrics.usagePercentage shouldBe 42.0
        point.networkInterfaceMetrics.single().values.bytesReceived shouldBe 1000L
    }

    @Test
    fun `averages the cpu temperature band into the one temperature the entry can hold`() {
        // Given
        val snapshot = snapshotOf(bucket(MetricId.CPU_TEMPERATURE, avgValue = 55.0))

        // When
        val point = SeriesEntryBuilder.systemPoint(timestamp, snapshot)

        // Then
        point.processorMetrics.cpuHealth.temperatures shouldContainExactly listOf(55.0)
    }

    @Test
    fun `reports no core loads, no smart data and no ip addresses`() {
        // Given
        val snapshot = snapshotOf(
            bucket(MetricId.CPU_USAGE_PERCENT, avgValue = 1.0),
            bucket(MetricId.DISK_READ_BYTES_PER_SECOND, itemId = "sda", avgValue = 10.0),
            bucket(MetricId.CONNECTIVITY_UP, minValue = 1.0, avgValue = 1.0, maxValue = 1.0)
        )

        // When
        val point = SeriesEntryBuilder.systemPoint(timestamp, snapshot)

        // Then
        point.processorMetrics.coreLoads.shouldBeEmpty()
        point.diskMetrics.single().smartData.shouldBeNull()
        point.diskMetrics.single().health.shouldBeNull()
        point.connectivity.externalIp.shouldBeNull()
        point.connectivity.previousExternalIp.shouldBeNull()
        point.connectivity.localIp.shouldBeNull()
    }

    @Test
    fun `falls the device name back to its id`() {
        // Given
        val snapshot = snapshotOf(
            bucket(MetricId.FILESYSTEM_FREE_BYTES, itemId = "fs-uuid-1", avgValue = 200.0),
            bucket(MetricId.DISK_READS, itemId = "sdb", avgValue = 5.0, lastValue = 5.0)
        )

        // When
        val point = SeriesEntryBuilder.systemPoint(timestamp, snapshot)

        // Then
        point.fileSystemMetrics.single().name shouldBe "fs-uuid-1"
        point.fileSystemMetrics.single().id shouldBe "fs-uuid-1"
        point.diskMetrics.single().name shouldBe "sdb"
    }

    @Test
    fun `holds a flag true only when it held for the whole bucket`() {
        // Given
        val held = snapshotOf(bucket(MetricId.CONNECTIVITY_UP, minValue = 1.0, avgValue = 1.0, maxValue = 1.0))
        val dipped = snapshotOf(bucket(MetricId.CONNECTIVITY_UP, minValue = 0.0, avgValue = 0.8, maxValue = 1.0))

        // When
        val heldPoint = SeriesEntryBuilder.systemPoint(timestamp, held)
        val dippedPoint = SeriesEntryBuilder.systemPoint(timestamp, dipped)

        // Then
        heldPoint.connectivity.connected shouldBe true
        dippedPoint.connectivity.connected shouldBe false
    }

    @Test
    fun `lists one device per item id across the metrics that mention it`() {
        // Given
        val snapshot = snapshotOf(
            bucket(MetricId.NETWORK_UP, itemId = "eth0", minValue = 1.0, avgValue = 1.0, maxValue = 1.0),
            bucket(MetricId.NETWORK_RX_BYTES_PER_SECOND, itemId = "eth0", avgValue = 10.0),
            bucket(MetricId.NETWORK_TX_BYTES_PER_SECOND, itemId = "eth1", avgValue = 20.0)
        )

        // When
        val point = SeriesEntryBuilder.systemPoint(timestamp, snapshot)

        // Then
        point.networkInterfaceMetrics.map { it.name } shouldContainExactly listOf("eth0", "eth1")
    }

    @Test
    fun `reports an empty device list when the series holds none`() {
        // Given
        val snapshot = snapshotOf(bucket(MetricId.CPU_USAGE_PERCENT, avgValue = 3.0))

        // When
        val point = SeriesEntryBuilder.systemPoint(timestamp, snapshot)

        // Then
        point.diskMetrics.shouldBeEmpty()
        point.fileSystemMetrics.shouldBeEmpty()
        point.gpuMetrics.shouldBeEmpty()
        point.networkInterfaceMetrics.shouldBeEmpty()
    }

    private fun snapshotOf(vararg buckets: com.krillsson.sysapi.core.history.series.MetricSeriesBucketEntity) =
        SeriesSnapshot(buckets.toList())

    private fun bucket(
        metric: MetricId,
        itemId: String = MetricId.HOST_WIDE,
        minValue: Double = 0.0,
        avgValue: Double = 0.0,
        maxValue: Double = 0.0,
        lastValue: Double = 0.0
    ) = seriesBucket(
        resolution = MetricResolution.FIVE_MINUTE,
        bucketStart = timestamp,
        samples = 5,
        minValue = minValue,
        avgValue = avgValue,
        maxValue = maxValue,
        lastValue = lastValue,
        metric = metric,
        itemId = itemId
    )
}
