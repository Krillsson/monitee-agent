package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommand
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class ProxmoxVmResolverTest {

    private val proxmoxService = mockk<ProxmoxService>()
    private val resolver = ProxmoxVmResolver(proxmoxService, mockk())

    private fun snapshot(quorate: Boolean) = ProxmoxSnapshot(
        cluster = ProxmoxCluster("homelab", quorate = quorate, nodeCount = 1),
        nodes = emptyList(),
        guests = emptyList(),
        storages = emptyList(),
        warnings = emptyList(),
        pveVersion = "9.0.3",
        lastSuccessfulPoll = Instant.parse("2026-11-14T10:00:00Z")
    )

    @Test
    fun `lists the commands of a running vm from the poll alone, without reading its run state`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot(quorate = true)

        // When
        val supported = resolver.supportedCommands(guest(100))

        // Then
        supported shouldContainExactly listOf(
            ProxmoxGuestCommand.SHUTDOWN,
            ProxmoxGuestCommand.FORCE_STOP,
            ProxmoxGuestCommand.REBOOT,
            ProxmoxGuestCommand.SUSPEND
        )
        verify(exactly = 0) { proxmoxService.guestDetail(any()) }
    }

    @Test
    fun `lists no commands while the cluster has no quorum`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot(quorate = false)

        // When
        val supported = resolver.supportedCommands(guest(100))

        // Then
        supported.shouldBeEmpty()
    }
}
