package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.smart.HealthStatus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SystemLoadMetricExtractorTest {

    private val timestamp = at("2026-09-12T10:00:00Z")

    @Test
    fun `averages the cpu temperature sensors into one sample`() {
        // Given
        val load = cpuLoad(temperatures = listOf(40.0, 60.0))

        // When
        val samples = SystemLoadMetricExtractor.cpu(load, timestamp)

        // Then
        samples.valueOf(MetricId.CPU_TEMPERATURE) shouldBe 50.0
    }

    @Test
    fun `records no cpu temperature when no sensor reports one`() {
        // Given
        val load = cpuLoad(temperatures = listOf(0.0, 0.0))

        // When
        val samples = SystemLoadMetricExtractor.cpu(load, timestamp)

        // Then
        samples.map { it.metric } shouldNotContain MetricId.CPU_TEMPERATURE
    }

    @Test
    fun `records each load average separately`() {
        // Given
        val load = cpuLoad()

        // When
        val samples = SystemLoadMetricExtractor.cpu(load, timestamp)

        // Then
        samples.valueOf(MetricId.LOAD_AVERAGE_1M) shouldBe 1.0
        samples.valueOf(MetricId.LOAD_AVERAGE_5M) shouldBe 2.0
        samples.valueOf(MetricId.LOAD_AVERAGE_15M) shouldBe 3.0
    }

    @Test
    fun `derives used bytes from total and available memory`() {
        // Given
        val load = memoryLoad(totalBytes = 1000, availableBytes = 250)

        // When
        val samples = SystemLoadMetricExtractor.memory(load, timestamp)

        // Then
        samples.valueOf(MetricId.MEMORY_USED_BYTES) shouldBe 750.0
        samples.valueOf(MetricId.MEMORY_TOTAL_BYTES) shouldBe 1000.0
    }

    @Test
    fun `identifies a network interface by name and records its up flag`() {
        // Given
        val loads = listOf(networkInterfaceLoad(name = "eth0", isUp = true), networkInterfaceLoad(name = "eth1", isUp = false))

        // When
        val samples = SystemLoadMetricExtractor.networkInterfaces(loads, timestamp)

        // Then
        samples.valueOf(MetricId.NETWORK_UP, "eth0") shouldBe 1.0
        samples.valueOf(MetricId.NETWORK_UP, "eth1") shouldBe 0.0
    }

    @Test
    fun `treats an unchanged external ip as stable`() {
        // Given
        val unchanged = connectivity(externalIp = "1.2.3.4", previousExternalIp = "1.2.3.4")
        val changed = connectivity(externalIp = "5.6.7.8", previousExternalIp = "1.2.3.4")

        // When
        val stable = SystemLoadMetricExtractor.connectivity(unchanged, timestamp)
        val unstable = SystemLoadMetricExtractor.connectivity(changed, timestamp)

        // Then
        stable.valueOf(MetricId.CONNECTIVITY_EXTERNAL_IP_STABLE) shouldBe 1.0
        unstable.valueOf(MetricId.CONNECTIVITY_EXTERNAL_IP_STABLE) shouldBe 0.0
    }

    @Test
    fun `records no ip stability sample when there is no external ip`() {
        // Given
        val offline = connectivity(connected = false, externalIp = null, previousExternalIp = null)

        // When
        val samples = SystemLoadMetricExtractor.connectivity(offline, timestamp)

        // Then
        samples.map { it.metric } shouldContainExactly listOf(MetricId.CONNECTIVITY_UP)
        samples.valueOf(MetricId.CONNECTIVITY_UP) shouldBe 0.0
    }

    @Test
    fun `identifies a disk by name and keeps rates apart from counters`() {
        // Given
        val loads = listOf(diskLoad(name = "sda", readBytesPerSecond = 1024, writeBytesPerSecond = 2048))

        // When
        val samples = SystemLoadMetricExtractor.diskRates(loads, timestamp)

        // Then
        samples.valueOf(MetricId.DISK_READ_BYTES_PER_SECOND, "sda") shouldBe 1024.0
        samples.valueOf(MetricId.DISK_WRITE_BYTES_PER_SECOND, "sda") shouldBe 2048.0
        samples.valueOf(MetricId.DISK_READ_BYTES, "sda") shouldBe 1000.0
    }

    @Test
    fun `records no rate sample when the speed is not yet measured`() {
        // Given
        val loads = listOf(diskLoad(readBytesPerSecond = -1, writeBytesPerSecond = -1))

        // When
        val samples = SystemLoadMetricExtractor.diskRates(loads, timestamp)

        // Then
        samples.map { it.metric } shouldNotContain MetricId.DISK_READ_BYTES_PER_SECOND
        samples.map { it.metric } shouldNotContain MetricId.DISK_WRITE_BYTES_PER_SECOND
    }

    @Test
    fun `collapses smart health to a flag that is only set when healthy`() {
        // Given
        val healthy = listOf(diskLoad(name = "sda", smartTemperature = 35, healthStatus = HealthStatus.HEALTHY))
        val failing = listOf(diskLoad(name = "sdb", smartTemperature = 55, healthStatus = HealthStatus.FAILING))

        // When
        val healthySamples = SystemLoadMetricExtractor.diskHealth(healthy, timestamp)
        val failingSamples = SystemLoadMetricExtractor.diskHealth(failing, timestamp)

        // Then
        healthySamples.valueOf(MetricId.DISK_SMART_HEALTHY, "sda") shouldBe 1.0
        healthySamples.valueOf(MetricId.DISK_TEMPERATURE, "sda") shouldBe 35.0
        failingSamples.valueOf(MetricId.DISK_SMART_HEALTHY, "sdb") shouldBe 0.0
    }

    @Test
    fun `records nothing for a disk without smart data`() {
        // Given
        val loads = listOf(diskLoad(smartTemperature = null, healthStatus = null))

        // When
        val samples = SystemLoadMetricExtractor.diskHealth(loads, timestamp)

        // Then
        samples.shouldBeEmpty()
    }

    @Test
    fun `derives used space from total and free space`() {
        // Given
        val loads = listOf(fileSystemLoad(id = "fs-1", freeSpaceBytes = 200, totalSpaceBytes = 500))

        // When
        val samples = SystemLoadMetricExtractor.fileSystems(loads, timestamp)

        // Then
        samples.valueOf(MetricId.FILESYSTEM_USED_BYTES, "fs-1") shouldBe 300.0
        samples.valueOf(MetricId.FILESYSTEM_FREE_BYTES, "fs-1") shouldBe 200.0
    }

    @Test
    fun `identifies a gpu by device id and skips unreported health fields`() {
        // Given
        val loads = listOf(gpuLoad(id = "gpu-0", coreLoad = 33.0, temperature = 65.0, powerDraw = 0.0))

        // When
        val samples = SystemLoadMetricExtractor.gpus(loads, timestamp)

        // Then
        samples.valueOf(MetricId.GPU_CORE_PERCENT, "gpu-0") shouldBe 33.0
        samples.valueOf(MetricId.GPU_TEMPERATURE, "gpu-0") shouldBe 65.0
        samples.map { it.metric } shouldNotContain MetricId.GPU_POWER_DRAW
    }
}

internal fun List<MetricSample>.valueOf(metric: MetricId, itemId: String = MetricId.HOST_WIDE): Double =
    single { it.metric == metric && it.itemId == itemId }.value
