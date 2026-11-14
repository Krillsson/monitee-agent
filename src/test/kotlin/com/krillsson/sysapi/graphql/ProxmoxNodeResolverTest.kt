package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.proxmox.ProxmoxNode
import com.krillsson.sysapi.proxmox.ProxmoxNodeStatus
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ProxmoxNodeResolverTest {

    private val proxmoxService = mockk<ProxmoxService>()
    private val resolver = ProxmoxNodeResolver(proxmoxService)

    private val node = ProxmoxNode(
        name = "pve1",
        status = ProxmoxNodeStatus.ONLINE,
        local = true,
        ip = null,
        uptimeSeconds = null,
        cpuUsagePercent = null,
        cpuCount = null,
        cpuModel = null,
        memoryUsedBytes = null,
        memoryTotalBytes = null,
        ksmSharedBytes = null,
        loadAverages = null,
        rootFsUsedBytes = null,
        rootFsTotalBytes = null,
        pveVersion = null,
        kernelRelease = null,
        detailUnavailableReason = null
    )

    @Test
    fun `splits the guests on a node into its vms and its containers`() {
        // Given
        every { proxmoxService.guests() } returns listOf(
            guest(100),
            guest(101, kind = ProxmoxGuestKind.LXC),
            guest(102, node = "pve2"),
            guest(103, kind = ProxmoxGuestKind.LXC, node = "pve2")
        )

        // When
        val virtualMachines = resolver.virtualMachines(node)
        val containers = resolver.containers(node)

        // Then
        virtualMachines.map { it.id } shouldContainExactly listOf("100")
        containers.map { it.id } shouldContainExactly listOf("101")
    }
}
