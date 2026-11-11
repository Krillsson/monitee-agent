package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

private val objectMapper = ObjectMapper()

fun fixtureBody(name: String): String =
    requireNotNull(ProxmoxJsonTest::class.java.getResource("/proxmox/$name")) { "No fixture $name" }.readText()

fun fixtureData(name: String): JsonNode = objectMapper.readTree(fixtureBody(name)).path("data")

fun guest(
    vmid: Int,
    kind: ProxmoxGuestKind = ProxmoxGuestKind.QEMU,
    name: String = "guest-$vmid",
    node: String = "pve1",
    status: ProxmoxGuestStatus = ProxmoxGuestStatus.RUNNING,
    template: Boolean = false,
    metrics: ProxmoxGuestMetrics? = if (status == ProxmoxGuestStatus.RUNNING) guestMetrics(vmid.toString(), kind) else null
): ProxmoxGuest = ProxmoxGuest(
    id = vmid.toString(),
    vmid = vmid,
    kind = kind,
    name = name,
    node = node,
    status = status,
    template = template,
    tags = emptyList(),
    haState = null,
    lock = null,
    uptimeSeconds = 60,
    cpuCount = 2,
    memoryTotalBytes = 1024,
    metrics = metrics
)

fun guestMetrics(id: String, kind: ProxmoxGuestKind = ProxmoxGuestKind.QEMU): ProxmoxGuestMetrics = ProxmoxGuestMetrics(
    id = id,
    cpuUsage = ProxmoxGuestCpuUsage(usagePercentTotal = 25.0),
    memoryUsage = ProxmoxGuestMemoryUsage(usageBytes = 512, usagePercent = 50.0, limitBytes = 1024, hostUsageBytes = null),
    networkUsage = ProxmoxGuestNetworkUsage(bytesReceived = 10, bytesTransferred = 20),
    blockIOUsage = ProxmoxGuestBlockIOUsage(bytesRead = 30, bytesWritten = 40),
    diskUsage = if (kind == ProxmoxGuestKind.LXC) ProxmoxGuestDiskUsage(usageBytes = 100, limitBytes = 200) else null
)
