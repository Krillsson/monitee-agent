package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxInfoAvailable
import com.krillsson.sysapi.graphql.domain.ProxmoxInfoUnavailable
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class QueryResolverTest {

    private val proxmoxService = mockk<ProxmoxService>()
    private val snapshot = mockk<ProxmoxSnapshot>()
    private val resolver = QueryResolver(
        monitorManager = mockk(),
        eventManager = mockk(),
        historyRepository = mockk(),
        genericEventRepository = mockk(),
        operatingSystem = mockk(),
        oshiOperatingSystem = mockk(),
        platform = mockk(),
        containerManager = mockk(),
        checkService = mockk(),
        pingProbe = mockk(),
        windowsEventLogManager = mockk(),
        systemDaemonManager = mockk(),
        serverIdService = mockk(),
        upsService = mockk(),
        legacyHistoryCompatService = mockk(),
        metricHistoryService = mockk(),
        storagePoolService = mockk(),
        proxmoxService = proxmoxService
    )

    @Test
    fun `answers proxmox with the snapshot when there is one`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot
        every { proxmoxService.status() } returns ProxmoxService.Status.Available

        // When
        val info = resolver.proxmox()

        // Then
        info shouldBe ProxmoxInfoAvailable(snapshot)
    }

    @Test
    fun `answers proxmox as disabled when it is turned off`() {
        // Given
        every { proxmoxService.snapshot() } returns null
        every { proxmoxService.status() } returns ProxmoxService.Status.Disabled

        // When
        val info = resolver.proxmox() as ProxmoxInfoUnavailable

        // Then
        info.isDisabled shouldBe true
    }

    @Test
    fun `answers proxmox with the reason it cannot be reached`() {
        // Given
        every { proxmoxService.snapshot() } returns null
        every { proxmoxService.status() } returns ProxmoxService.Status.Unavailable("Connection refused")

        // When
        val info = resolver.proxmox()

        // Then
        info shouldBe ProxmoxInfoUnavailable("Connection refused", isDisabled = false)
    }

    @Test
    fun `answers proxmox as unavailable when the first poll lands after the snapshot was read`() {
        // Given
        every { proxmoxService.snapshot() } returns null
        every { proxmoxService.status() } returns ProxmoxService.Status.Available

        // When
        val info = resolver.proxmox() as ProxmoxInfoUnavailable

        // Then
        info.isDisabled shouldBe false
    }
}
