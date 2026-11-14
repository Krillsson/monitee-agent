package com.krillsson.sysapi.docker

import com.krillsson.sysapi.config.CacheConfiguration
import com.krillsson.sysapi.config.DockerConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.docker.BlockIOUsage
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.docker.CpuUsage
import com.krillsson.sysapi.core.domain.docker.MemoryUsage
import com.krillsson.sysapi.core.domain.docker.NetworkUsage
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.domain.docker.ThrottlingData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant

class ContainerServiceTest {

    private val dockerClient = mockk<DockerClient> {
        every { checkAvailability() } returns DockerClient.PingResult.Success
    }
    private var now = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = mockk<Clock> { every { instant() } answers { now } }
    private val configFile = mockk<YAMLConfigFile> { every { docker } returns DockerConfiguration() }
    private val service = ContainerService(dockerClient, configFile, mockk(), clock)
    private val web = container("abc", "web")

    private fun metrics(id: String) = ContainerMetrics(
        id = id,
        cpuUsage = CpuUsage(usagePercentPerCore = 40.0, usagePercentTotal = 10.0, throttlingData = ThrottlingData(0, 0, 0)),
        memoryUsage = MemoryUsage(usageBytes = 256, usagePercent = 25.0, limitBytes = 1024),
        currentPid = 1,
        networkUsage = NetworkUsage(bytesReceived = 0, bytesTransferred = 0),
        blockIOUsage = BlockIOUsage(bytesWritten = 0, bytesRead = 0)
    )

    @Test
    fun `reads the last sample and never asks docker for stats`() {
        // Given
        every { dockerClient.containerStatistics("abc") } returns metrics("abc")
        service.statsForContainer("abc")
        now = now.plusSeconds(30)

        // When
        val sampled = service.lastSampledMetrics(web)

        // Then
        sampled shouldBe ContainerService.SampledMetrics(metrics("abc"), Instant.parse("2026-11-14T10:00:00Z"))
        verify(exactly = 1) { dockerClient.containerStatistics("abc") }
    }

    @Test
    fun `has no sample for a container that has not been sampled yet`() {
        // When
        val sampled = service.lastSampledMetrics(web)

        // Then
        sampled.shouldBeNull()
        verify(exactly = 0) { dockerClient.containerStatistics(any()) }
    }

    @Test
    fun `has no sample for a container that is not running`() {
        // Given
        every { dockerClient.containerStatistics("abc") } returns metrics("abc")
        service.statsForContainer("abc")

        // When
        val sampled = service.lastSampledMetrics(web.copy(state = State.EXITED))

        // Then
        sampled.shouldBeNull()
    }

    @Test
    fun `forgets the sample when docker no longer has stats for the container`() {
        // Given
        val uncached = mockk<YAMLConfigFile> {
            every { docker } returns DockerConfiguration(cache = CacheConfiguration(enabled = false))
        }
        val service = ContainerService(dockerClient, uncached, mockk(), clock)
        every { dockerClient.containerStatistics("abc") } returns metrics("abc")
        service.statsForContainer("abc")
        every { dockerClient.containerStatistics("abc") } returns null

        // When
        service.statsForContainer("abc")

        // Then
        service.lastSampledMetrics(web).shouldBeNull()
    }

    @Test
    fun `forgets samples older than an hour`() {
        // Given
        every { dockerClient.containerStatistics(any()) } answers { metrics(firstArg()) }
        service.statsForContainer("abc")
        now = now.plusSeconds(3601)

        // When
        service.statsForContainer("def")

        // Then
        service.lastSampledMetrics(web).shouldBeNull()
        service.lastSampledMetrics(container("def", "db"))?.sampledAt shouldBe now
    }
}
