package com.krillsson.sysapi.workload.proxmox

import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot
import com.krillsson.sysapi.proxmox.guest
import com.krillsson.sysapi.workload.PlatformAvailability
import com.krillsson.sysapi.workload.RunState
import com.krillsson.sysapi.workload.WorkloadKind
import com.krillsson.sysapi.workload.WorkloadPlatform
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant

class ProxmoxWorkloadSourceTest {

    private val proxmoxService = mockk<ProxmoxService>()
    private val source = ProxmoxWorkloadSource(proxmoxService)
    private val polledAt = Instant.parse("2026-11-14T10:00:00Z")

    private fun snapshotWith(vararg guests: ProxmoxGuest) {
        every { proxmoxService.snapshot() } returns ProxmoxSnapshot(
            cluster = ProxmoxCluster("homelab", quorate = true, nodeCount = 1),
            nodes = emptyList(),
            guests = guests.toList(),
            storages = emptyList(),
            warnings = emptyList(),
            pveVersion = "9.0.3",
            lastSuccessfulPoll = polledAt
        )
        every { proxmoxService.status() } returns ProxmoxService.Status.Available
    }

    @Test
    fun `lists qemu guests as virtual machines and lxc guests as containers`() {
        // Given
        snapshotWith(guest(100), guest(101, kind = ProxmoxGuestKind.LXC, status = ProxmoxGuestStatus.STOPPED))

        // When
        val reading = source.read()

        // Then
        reading.workloads.map { Triple(it.id, it.kind, it.runState) } shouldContainExactly listOf(
            Triple("100", WorkloadKind.VIRTUAL_MACHINE, RunState.RUNNING),
            Triple("101", WorkloadKind.CONTAINER, RunState.STOPPED)
        )
        reading.availability shouldBe PlatformAvailability(
            WorkloadPlatform.PROXMOX,
            available = true,
            isDisabled = false,
            reason = null,
            lastSuccessfulPoll = polledAt
        )
    }

    @Test
    fun `lists exactly the guests the snapshot holds, so templates only when the service kept them`() {
        // Given
        snapshotWith(guest(100), guest(9000, template = true))

        // When
        val reading = source.read()

        // Then
        reading.workloads.map { it.source } shouldContainExactly listOf(guest(100), guest(9000, template = true))
    }

    @Test
    fun `reports a disabled proxmox as disabled with nothing listed`() {
        // Given
        every { proxmoxService.snapshot() } returns null
        every { proxmoxService.status() } returns ProxmoxService.Status.Disabled

        // When
        val reading = source.read()

        // Then
        reading.workloads.shouldBeEmpty()
        reading.availability.isDisabled shouldBe true
        reading.availability.available shouldBe false
    }

    @Test
    fun `passes on why proxmox is unavailable`() {
        // Given
        every { proxmoxService.snapshot() } returns null
        every { proxmoxService.status() } returns ProxmoxService.Status.Unavailable("Proxmox rejected the API token")

        // When
        val reading = source.read()

        // Then
        reading.availability.reason shouldBe "Proxmox rejected the API token"
        reading.availability.isDisabled shouldBe false
    }
}
