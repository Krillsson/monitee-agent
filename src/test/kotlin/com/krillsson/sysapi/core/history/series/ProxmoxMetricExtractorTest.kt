package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxNode
import com.krillsson.sysapi.proxmox.ProxmoxNodeStatus
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProxmoxMetricExtractorTest {

    private val timestamp = at("2026-11-11T10:00:00Z")

    @Test
    fun `records a stopped guest as not running and nothing else`() {
        // Given
        val guests = listOf(guest(102, status = ProxmoxGuestStatus.STOPPED))

        // When
        val samples = ProxmoxMetricExtractor.guests(guests, timestamp)

        // Then
        samples.map { it.metric } shouldContainExactly listOf(MetricId.PROXMOX_GUEST_RUNNING)
        samples.valueOf(MetricId.PROXMOX_GUEST_RUNNING, "102") shouldBe 0.0
    }

    @Test
    fun `keys a running guest's samples by its vmid`() {
        // Given
        val guests = listOf(guest(100))

        // When
        val samples = ProxmoxMetricExtractor.guests(guests, timestamp)

        // Then
        samples.valueOf(MetricId.PROXMOX_GUEST_RUNNING, "100") shouldBe 1.0
        samples.valueOf(MetricId.PROXMOX_GUEST_CPU_PERCENT, "100") shouldBe 25.0
        samples.valueOf(MetricId.PROXMOX_GUEST_MEMORY_USED_BYTES, "100") shouldBe 512.0
        samples.valueOf(MetricId.PROXMOX_GUEST_MEMORY_TOTAL_BYTES, "100") shouldBe 1024.0
        samples.valueOf(MetricId.PROXMOX_GUEST_RX_BYTES, "100") shouldBe 10.0
        samples.valueOf(MetricId.PROXMOX_GUEST_BLOCK_WRITE_BYTES, "100") shouldBe 40.0
    }

    @Test
    fun `records disk usage for containers only`() {
        // Given
        val guests = listOf(guest(100), guest(101, kind = ProxmoxGuestKind.LXC))

        // When
        val samples = ProxmoxMetricExtractor.guests(guests, timestamp)

        // Then
        samples.filter { it.metric == MetricId.PROXMOX_GUEST_DISK_USED_BYTES }.map { it.itemId } shouldContainExactly listOf("101")
    }

    @Test
    fun `leaves templates out`() {
        // Given
        val guests = listOf(guest(9000, status = ProxmoxGuestStatus.STOPPED, template = true))

        // When
        val samples = ProxmoxMetricExtractor.guests(guests, timestamp)

        // Then
        samples.shouldBeEmpty()
    }

    @Test
    fun `keys node samples by node name and skips what an offline node does not report`() {
        // Given
        val nodes = listOf(node("pve1", cpuUsagePercent = 4.2, memoryUsedBytes = 1000), node("pve3"))

        // When
        val samples = ProxmoxMetricExtractor.nodes(nodes, timestamp)

        // Then
        samples.valueOf(MetricId.PROXMOX_NODE_CPU_PERCENT, "pve1") shouldBe 4.2
        samples.valueOf(MetricId.PROXMOX_NODE_MEMORY_USED_BYTES, "pve1") shouldBe 1000.0
        samples.filter { it.itemId == "pve3" }.shouldBeEmpty()
    }

    private fun node(name: String, cpuUsagePercent: Double? = null, memoryUsedBytes: Long? = null) = ProxmoxNode(
        name = name,
        status = if (cpuUsagePercent != null) ProxmoxNodeStatus.ONLINE else ProxmoxNodeStatus.OFFLINE,
        local = false,
        ip = null,
        uptimeSeconds = null,
        cpuUsagePercent = cpuUsagePercent,
        cpuCount = null,
        cpuModel = null,
        memoryUsedBytes = memoryUsedBytes,
        memoryTotalBytes = null,
        ksmSharedBytes = null,
        loadAverages = null,
        rootFsUsedBytes = null,
        rootFsTotalBytes = null,
        pveVersion = null,
        kernelRelease = null,
        detailUnavailableReason = null
    )
}
