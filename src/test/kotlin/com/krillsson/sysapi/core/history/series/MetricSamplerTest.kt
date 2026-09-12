package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.core.metrics.CpuMetrics
import com.krillsson.sysapi.core.metrics.DiskMetrics
import com.krillsson.sysapi.core.metrics.FileSystemMetrics
import com.krillsson.sysapi.core.metrics.GpuMetrics
import com.krillsson.sysapi.core.metrics.MemoryMetrics
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.metrics.NetworkMetrics
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.smart.HealthStatus
import com.krillsson.sysapi.ups.UpsService
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler

class MetricSamplerTest {

    private val cpuMetrics = mockk<CpuMetrics>(relaxed = true)
    private val memoryMetrics = mockk<MemoryMetrics>(relaxed = true)
    private val networkMetrics = mockk<NetworkMetrics>(relaxed = true)
    private val gpuMetrics = mockk<GpuMetrics>(relaxed = true)
    private val diskMetrics = mockk<DiskMetrics>(relaxed = true)
    private val fileSystemMetrics = mockk<FileSystemMetrics>(relaxed = true)
    private val metrics = mockk<Metrics>(relaxed = true)
    private val containerService = mockk<ContainerService>(relaxed = true)
    private val upsService = mockk<UpsService>(relaxed = true)
    private val repository = mockk<MetricSeriesBucketRepository>(relaxed = true)
    private val taskScheduler = mockk<TaskScheduler>(relaxed = true)

    private val saved = mutableListOf<MetricSeriesBucketEntity>()

    private val sampler = MetricSampler(
        metrics,
        containerService,
        upsService,
        repository,
        taskScheduler,
        configWithSeriesRetention()
    )

    @BeforeEach
    fun setUp() {
        saved.clear()
        every { metrics.cpuMetrics() } returns cpuMetrics
        every { metrics.memoryMetrics() } returns memoryMetrics
        every { metrics.networkMetrics() } returns networkMetrics
        every { metrics.gpuMetrics() } returns gpuMetrics
        every { metrics.diskMetrics() } returns diskMetrics
        every { metrics.fileSystemMetrics() } returns fileSystemMetrics
        every { cpuMetrics.cpuLoad() } returns cpuLoad()
        every { memoryMetrics.memoryLoad() } returns memoryLoad()
        every { networkMetrics.networkInterfaceLoads() } returns listOf(networkInterfaceLoad())
        every { networkMetrics.connectivity() } returns connectivity()
        every { gpuMetrics.gpuLoads() } returns listOf(gpuLoad())
        every { diskMetrics.diskLoadsExcludingSmartData() } returns listOf(diskLoad())
        every { diskMetrics.diskLoads() } returns listOf(
            diskLoad(smartTemperature = 35, healthStatus = HealthStatus.HEALTHY)
        )
        every { fileSystemMetrics.fileSystemLoads() } returns listOf(fileSystemLoad())
        every { containerService.containers() } returns emptyList()
        every { upsService.upsDevices() } returns emptyList()
        every { repository.saveAll(any<Iterable<MetricSeriesBucketEntity>>()) } answers {
            firstArg<Iterable<MetricSeriesBucketEntity>>().toMutableList().also { saved += it }
        }
    }

    @Test
    fun `writes every sample as a raw row carrying one sample`() {
        // When
        sampler.sampleFast()

        // Then
        saved.forEach { row ->
            row.resolution shouldBe MetricResolution.RAW
            row.samples shouldBe 1
            row.minValue shouldBe row.avgValue
            row.maxValue shouldBe row.avgValue
            row.lastValue shouldBe row.avgValue
        }
    }

    @Test
    fun `reads disk rates without asking for smart data`() {
        // When
        sampler.sampleFast()

        // Then
        verify { diskMetrics.diskLoadsExcludingSmartData() }
        verify(exactly = 0) { diskMetrics.diskLoads() }
    }

    @Test
    fun `samples the cheap sources on the fast pass and leaves the expensive ones out`() {
        // When
        sampler.sampleFast()

        // Then
        val sampled = saved.map { it.metric }
        sampled shouldContain MetricId.CPU_USAGE_PERCENT
        sampled shouldContain MetricId.MEMORY_USED_BYTES
        sampled shouldContain MetricId.DISK_READ_BYTES_PER_SECOND
        sampled shouldNotContain MetricId.FILESYSTEM_FREE_BYTES
        sampled shouldNotContain MetricId.DISK_SMART_HEALTHY
    }

    @Test
    fun `samples the expensive sources on the slow pass`() {
        // When
        sampler.sampleSlow()

        // Then
        val sampled = saved.map { it.metric }
        sampled shouldContain MetricId.FILESYSTEM_FREE_BYTES
        sampled shouldContain MetricId.DISK_SMART_HEALTHY
        sampled shouldNotContain MetricId.CPU_USAGE_PERCENT
    }

    @Test
    fun `keeps sampling the other sources when one of them fails`() {
        // Given
        every { gpuMetrics.gpuLoads() } throws IllegalStateException("no gpu driver")

        // When
        sampler.sampleFast()

        // Then
        val sampled = saved.map { it.metric }
        sampled shouldContain MetricId.CPU_USAGE_PERCENT
        sampled shouldNotContain MetricId.GPU_CORE_PERCENT
    }

    @Test
    fun `writes nothing when there is nothing to sample`() {
        // Given
        every { cpuMetrics.cpuLoad() } throws IllegalStateException("unavailable")
        every { memoryMetrics.memoryLoad() } throws IllegalStateException("unavailable")
        every { networkMetrics.networkInterfaceLoads() } throws IllegalStateException("unavailable")
        every { gpuMetrics.gpuLoads() } throws IllegalStateException("unavailable")
        every { diskMetrics.diskLoadsExcludingSmartData() } throws IllegalStateException("unavailable")

        // When
        sampler.sampleFast()

        // Then
        saved.shouldBeEmpty()
        verify(exactly = 0) { repository.saveAll(any<Iterable<MetricSeriesBucketEntity>>()) }
    }
}
