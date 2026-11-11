package com.krillsson.sysapi.mqtt

import com.krillsson.sysapi.config.MqttConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.Status
import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot
import com.krillsson.sysapi.proxmox.guest
import com.krillsson.sysapi.ups.UpsService
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MqttEntityCollectorTest {

    private val proxmoxService = mockk<ProxmoxService>()

    private fun collector(proxmoxGuests: Boolean = false): MqttEntityCollector {
        val configFile = mockk<YAMLConfigFile> {
            every { mqtt } returns MqttConfiguration(proxmoxGuests = proxmoxGuests)
        }
        return MqttEntityCollector(
            configFile = configFile,
            metrics = mockk<Metrics>(relaxed = true),
            monitorManager = mockk(relaxed = true),
            eventManager = mockk(relaxed = true),
            containerService = mockk<ContainerService> { every { status } returns Status.Disabled },
            containerUpdateChecker = mockk(relaxed = true),
            upsService = mockk<UpsService> { every { status() } returns UpsService.Status.Disabled },
            proxmoxService = proxmoxService,
            clock = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC)
        )
    }

    private fun snapshot(cluster: ProxmoxCluster = ProxmoxCluster(name = null, quorate = true, nodeCount = 1)) = ProxmoxSnapshot(
        cluster = cluster,
        nodes = emptyList(),
        guests = listOf(
            guest(100, name = "web"),
            guest(101, name = "web", kind = ProxmoxGuestKind.LXC),
            guest(102, name = "db", status = ProxmoxGuestStatus.STOPPED)
        ),
        storages = emptyList(),
        warnings = emptyList(),
        pveVersion = "9.0.3",
        lastSuccessfulPoll = Instant.parse("2026-11-11T10:00:00Z")
    )

    private fun List<MeasuredEntity>.proxmoxKeys() = map { it.entity.key }.filter { it.startsWith("proxmox_") }

    @Test
    fun `publishes nothing for proxmox while it is unavailable`() {
        // Given
        every { proxmoxService.snapshot() } returns null

        // When
        val entities = collector().collect()

        // Then
        entities.proxmoxKeys().shouldBeEmpty()
    }

    @Test
    fun `counts running guests and leaves out per guest sensors and quorum on a standalone node`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot()

        // When
        val entities = collector().collect()

        // Then
        entities.proxmoxKeys() shouldContainExactly listOf("proxmox_guests_running", "proxmox_nodes_online")
        entities.single { it.entity.key == "proxmox_guests_running" }.value shouldBe 2
    }

    @Test
    fun `reports a lost quorum as a problem on a cluster`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot(ProxmoxCluster(name = "homelab", quorate = false, nodeCount = 3))

        // When
        val entities = collector().collect()

        // Then
        entities.single { it.entity.key == "proxmox_quorum" }.value shouldBe "ON"
    }

    @Test
    fun `adds a running sensor per guest, keyed so guests sharing a name stay apart`() {
        // Given
        every { proxmoxService.snapshot() } returns snapshot()

        // When
        val entities = collector(proxmoxGuests = true).collect()

        // Then
        val guests = entities.filter { it.entity.key.startsWith("proxmox_guest_") }
        guests.map { it.entity.key } shouldContainExactly listOf(
            "proxmox_guest_web_100_running",
            "proxmox_guest_web_101_running",
            "proxmox_guest_db_102_running"
        )
        guests.map { it.value } shouldContainExactly listOf("ON", "ON", "OFF")
    }
}
