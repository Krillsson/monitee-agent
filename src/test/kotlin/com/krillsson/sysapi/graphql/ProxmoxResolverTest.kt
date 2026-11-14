package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxInfoAvailable
import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class ProxmoxResolverTest {

    private val resolver = ProxmoxResolver()

    private val info = ProxmoxInfoAvailable(
        ProxmoxSnapshot(
            cluster = ProxmoxCluster("homelab", quorate = true, nodeCount = 1),
            nodes = emptyList(),
            guests = listOf(guest(100), guest(101, status = ProxmoxGuestStatus.STOPPED)),
            storages = emptyList(),
            warnings = emptyList(),
            pveVersion = "9.0.3",
            lastSuccessfulPoll = Instant.parse("2026-11-11T10:00:00Z")
        )
    )

    @Test
    fun `answers every field from the snapshot the query started with`() {
        // When
        val cluster = resolver.cluster(info)

        // Then
        cluster.name shouldBe "homelab"
        resolver.pveVersion(info) shouldBe "9.0.3"
        resolver.lastSuccessfulPoll(info) shouldBe Instant.parse("2026-11-11T10:00:00Z")
    }
}
