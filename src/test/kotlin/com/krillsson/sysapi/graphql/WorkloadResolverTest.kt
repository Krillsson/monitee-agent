package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.BlockIOUsage
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.docker.CpuUsage
import com.krillsson.sysapi.core.domain.docker.MemoryUsage
import com.krillsson.sysapi.core.domain.docker.NetworkUsage
import com.krillsson.sysapi.core.domain.docker.ThrottlingData
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.container
import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot
import com.krillsson.sysapi.proxmox.guest
import com.krillsson.sysapi.workload.WorkloadUsage
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class WorkloadResolverTest {

    private val containerService = mockk<ContainerService>()
    private val proxmoxService = mockk<ProxmoxService>()
    private val resolver = WorkloadResolver(mockk(), containerService, proxmoxService)
    private val sampledAt = Instant.parse("2026-11-14T10:00:00Z")

    private fun dockerMetrics(limitBytes: Long) = ContainerMetrics(
        id = "abc",
        cpuUsage = CpuUsage(usagePercentPerCore = 80.0, usagePercentTotal = 20.0, throttlingData = ThrottlingData(0, 0, 0)),
        memoryUsage = MemoryUsage(usageBytes = 256, usagePercent = 25.0, limitBytes = limitBytes),
        currentPid = 1,
        networkUsage = NetworkUsage(bytesReceived = 0, bytesTransferred = 0),
        blockIOUsage = BlockIOUsage(bytesWritten = 0, bytesRead = 0)
    )

    @Test
    fun `names a docker container after its first name without the leading slash`() {
        // Given
        val container = container("abc", "web").copy(names = listOf("/web", "/alias"))

        // When
        val name = resolver.dockerName(container)

        // Then
        name shouldBe "web"
    }

    @Test
    fun `answers docker usage from the last sample without asking docker for stats`() {
        // Given
        val container = container("abc", "web")
        every { containerService.lastSampledMetrics(container) } returns
            ContainerService.SampledMetrics(dockerMetrics(limitBytes = 1024), sampledAt)

        // When
        val usage = resolver.dockerUsage(container)

        // Then
        usage shouldBe WorkloadUsage(cpuPercent = 20.0, memoryUsedBytes = 256, memoryLimitBytes = 1024, sampledAt = sampledAt)
        verify(exactly = 0) { containerService.statsForContainer(any()) }
    }

    @Test
    fun `answers no docker usage before the container has been sampled`() {
        // Given
        val container = container("abc", "web")
        every { containerService.lastSampledMetrics(container) } returns null

        // When
        val usage = resolver.dockerUsage(container)

        // Then
        usage.shouldBeNull()
    }

    @Test
    fun `answers no memory limit when docker reports none`() {
        // Given
        val container = container("abc", "web")
        every { containerService.lastSampledMetrics(container) } returns
            ContainerService.SampledMetrics(dockerMetrics(limitBytes = 0), sampledAt)

        // When
        val usage = resolver.dockerUsage(container)

        // Then
        usage?.memoryLimitBytes.shouldBeNull()
    }

    @Test
    fun `answers proxmox usage from the guest's metrics, read at the last poll`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot()

        // When
        val usage = resolver.proxmoxVmUsage(guest(100))

        // Then
        usage shouldBe WorkloadUsage(cpuPercent = 25.0, memoryUsedBytes = 512, memoryLimitBytes = 1024, sampledAt = sampledAt)
    }

    @Test
    fun `answers no proxmox usage for a stopped guest`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot()

        // When
        val usage = resolver.proxmoxContainerUsage(guest(101, status = ProxmoxGuestStatus.STOPPED))

        // Then
        usage.shouldBeNull()
    }

    private fun snapshot() = ProxmoxSnapshot(
        cluster = ProxmoxCluster("homelab", quorate = true, nodeCount = 1),
        nodes = emptyList(),
        guests = emptyList(),
        storages = emptyList(),
        warnings = emptyList(),
        pveVersion = "9.0.3",
        lastSuccessfulPoll = sampledAt
    )
}
