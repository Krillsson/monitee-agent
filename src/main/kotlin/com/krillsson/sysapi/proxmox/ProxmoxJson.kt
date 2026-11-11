package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.databind.JsonNode

object ProxmoxJson {

    data class Resources(
        val nodes: List<ResourceNode>,
        val guests: List<ProxmoxGuest>,
        val storages: List<ProxmoxStorage>
    )

    data class ResourceNode(
        val name: String,
        val status: ProxmoxNodeStatus,
        val uptimeSeconds: Long?,
        val cpuUsagePercent: Double?,
        val cpuCount: Int?,
        val memoryUsedBytes: Long?,
        val memoryTotalBytes: Long?
    )

    data class ClusterStatus(
        val cluster: ProxmoxCluster,
        val nodes: Map<String, ClusterNode>
    )

    data class ClusterNode(val ip: String?, val local: Boolean)

    data class NodeDetail(
        val cpuModel: String?,
        val cpuCount: Int?,
        val ksmSharedBytes: Long?,
        val loadAverages: List<Double>?,
        val rootFsUsedBytes: Long?,
        val rootFsTotalBytes: Long?,
        val pveVersion: String?,
        val kernelRelease: String?
    )

    private val tagSeparators = Regex("[;,\\s]+")
    private const val LOOPBACK = "lo"

    fun version(data: JsonNode): String = data.text("version") ?: data.text("release") ?: "unknown"

    fun resources(data: JsonNode): Resources {
        val rows = data.toList()
        return Resources(
            nodes = rows.filter { it.text("type") == "node" }.mapNotNull { resourceNode(it) },
            guests = rows.mapNotNull { guest(it) },
            storages = rows.filter { it.text("type") == "storage" }.mapNotNull { storage(it) }
        )
    }

    fun clusterStatus(data: JsonNode): ClusterStatus {
        val rows = data.toList()
        val nodes = rows.filter { it.text("type") == "node" }
            .mapNotNull { row ->
                row.text("name")?.let { it to ClusterNode(ip = row.text("ip"), local = row.flag("local") == true) }
            }
            .toMap()
        val cluster = rows.firstOrNull { it.text("type") == "cluster" }
            ?.let {
                ProxmoxCluster(
                    name = it.text("name"),
                    quorate = it.flag("quorate") ?: false,
                    nodeCount = it.long("nodes")?.toInt() ?: nodes.size
                )
            }
            ?: ProxmoxCluster(name = null, quorate = true, nodeCount = nodes.size)
        return ClusterStatus(cluster, nodes)
    }

    fun nodeStatus(data: JsonNode): NodeDetail = NodeDetail(
        cpuModel = data.path("cpuinfo").text("model"),
        cpuCount = data.path("cpuinfo").long("cpus")?.toInt(),
        ksmSharedBytes = data.path("ksm").long("shared"),
        loadAverages = data.path("loadavg").takeIf { it.isArray }?.mapNotNull { it.number() },
        rootFsUsedBytes = data.path("rootfs").long("used"),
        rootFsTotalBytes = data.path("rootfs").long("total"),
        pveVersion = data.text("pveversion"),
        kernelRelease = data.path("current-kernel").text("release") ?: data.text("kversion")
    )

    fun guestDetail(kind: ProxmoxGuestKind, status: JsonNode, config: JsonNode): ProxmoxGuestDetail = when (kind) {
        ProxmoxGuestKind.QEMU -> ProxmoxGuestDetail(
            qmpStatus = status.text("qmpstatus") ?: status.text("status"),
            agentEnabled = config.text("agent")?.let { agentEnabled(it) } ?: status.flag("agent") ?: false,
            osType = config.text("ostype"),
            hostname = null,
            unprivileged = null,
            swapUsedBytes = null,
            swapTotalBytes = null
        )

        ProxmoxGuestKind.LXC -> ProxmoxGuestDetail(
            qmpStatus = null,
            agentEnabled = null,
            osType = config.text("ostype"),
            hostname = config.text("hostname"),
            unprivileged = config.flag("unprivileged") ?: false,
            swapUsedBytes = status.long("swap"),
            swapTotalBytes = status.long("maxswap")
        )
    }

    fun qemuNetwork(interfaces: JsonNode, hostName: JsonNode?): ProxmoxGuestNetwork = ProxmoxGuestNetwork(
        hostname = hostName?.agentResult()?.text("host-name"),
        interfaces = interfaces.agentResult()
            .filter { it.text("name") != LOOPBACK }
            .mapNotNull { networkInterface ->
                val name = networkInterface.text("name") ?: return@mapNotNull null
                val addresses = networkInterface.path("ip-addresses").toList()
                ProxmoxGuestNetworkInterface(
                    name = name,
                    macAddress = networkInterface.text("hardware-address"),
                    ipv4 = addresses.addressesOfType("ipv4"),
                    ipv6 = addresses.addressesOfType("ipv6")
                )
            }
    )

    fun lxcNetwork(interfaces: JsonNode, hostname: String?): ProxmoxGuestNetwork = ProxmoxGuestNetwork(
        hostname = hostname,
        interfaces = interfaces.toList()
            .filter { it.text("name") != LOOPBACK }
            .mapNotNull { networkInterface ->
                val name = networkInterface.text("name") ?: return@mapNotNull null
                val addresses = networkInterface.path("ip-addresses").toList()
                ProxmoxGuestNetworkInterface(
                    name = name,
                    macAddress = networkInterface.text("hwaddr") ?: networkInterface.text("hardware-address"),
                    ipv4 = addresses.addressesOfType("inet")
                        .ifEmpty { listOfNotNull(networkInterface.text("inet")?.substringBefore('/')) },
                    ipv6 = addresses.addressesOfType("inet6")
                        .ifEmpty { listOfNotNull(networkInterface.text("inet6")?.substringBefore('/')) }
                )
            }
    )

    fun agentEnabled(value: String): Boolean {
        val options = value.split(',').map { it.trim() }
        val enabled = options.firstOrNull { !it.contains('=') }
            ?: options.firstOrNull { it.startsWith("enabled=") }?.substringAfter('=')
        return enabled?.let { booleanOption(it) } ?: false
    }

    private fun resourceNode(row: JsonNode): ResourceNode? {
        val name = row.text("node") ?: return null
        return ResourceNode(
            name = name,
            status = when (row.text("status")) {
                "online" -> ProxmoxNodeStatus.ONLINE
                "offline" -> ProxmoxNodeStatus.OFFLINE
                else -> ProxmoxNodeStatus.UNKNOWN
            },
            uptimeSeconds = row.long("uptime"),
            cpuUsagePercent = row.number("cpu")?.let { it * 100 },
            cpuCount = row.long("maxcpu")?.toInt(),
            memoryUsedBytes = row.long("mem"),
            memoryTotalBytes = row.long("maxmem")
        )
    }

    private fun guest(row: JsonNode): ProxmoxGuest? {
        val kind = when (row.text("type")) {
            "qemu" -> ProxmoxGuestKind.QEMU
            "lxc" -> ProxmoxGuestKind.LXC
            else -> return null
        }
        val vmid = row.long("vmid")?.toInt() ?: return null
        val node = row.text("node") ?: return null
        val id = vmid.toString()
        val status = when (row.text("status")) {
            "running" -> ProxmoxGuestStatus.RUNNING
            "stopped" -> ProxmoxGuestStatus.STOPPED
            else -> ProxmoxGuestStatus.UNKNOWN
        }
        val memoryTotal = row.long("maxmem") ?: 0
        return ProxmoxGuest(
            id = id,
            vmid = vmid,
            kind = kind,
            name = row.text("name")?.takeIf { it.isNotBlank() } ?: defaultName(kind, vmid),
            node = node,
            status = status,
            template = row.flag("template") ?: false,
            tags = row.text("tags")?.split(tagSeparators)?.filter { it.isNotBlank() }.orEmpty(),
            haState = row.text("hastate"),
            lock = row.text("lock")?.takeIf { it.isNotBlank() },
            uptimeSeconds = row.long("uptime") ?: 0,
            cpuCount = row.number("maxcpu")?.toInt() ?: 0,
            memoryTotalBytes = memoryTotal,
            metrics = if (status == ProxmoxGuestStatus.RUNNING) guestMetrics(id, kind, row, memoryTotal) else null
        )
    }

    private fun guestMetrics(id: String, kind: ProxmoxGuestKind, row: JsonNode, memoryTotal: Long): ProxmoxGuestMetrics {
        val memoryUsed = row.long("mem") ?: 0
        return ProxmoxGuestMetrics(
            id = id,
            cpuUsage = ProxmoxGuestCpuUsage(usagePercentTotal = (row.number("cpu") ?: 0.0) * 100),
            memoryUsage = ProxmoxGuestMemoryUsage(
                usageBytes = memoryUsed,
                usagePercent = if (memoryTotal > 0) memoryUsed * 100.0 / memoryTotal else 0.0,
                limitBytes = memoryTotal,
                hostUsageBytes = row.long("memhost")
            ),
            networkUsage = ProxmoxGuestNetworkUsage(
                bytesReceived = row.long("netin") ?: 0,
                bytesTransferred = row.long("netout") ?: 0
            ),
            blockIOUsage = ProxmoxGuestBlockIOUsage(
                bytesRead = row.long("diskread") ?: 0,
                bytesWritten = row.long("diskwrite") ?: 0
            ),
            diskUsage = when (kind) {
                ProxmoxGuestKind.QEMU -> null
                ProxmoxGuestKind.LXC -> ProxmoxGuestDiskUsage(
                    usageBytes = row.long("disk") ?: 0,
                    limitBytes = row.long("maxdisk") ?: 0
                )
            }
        )
    }

    private fun storage(row: JsonNode): ProxmoxStorage? {
        val node = row.text("node") ?: return null
        val storage = row.text("storage") ?: return null
        return ProxmoxStorage(
            id = "$node/$storage",
            storage = storage,
            node = node,
            type = row.text("plugintype"),
            status = if (row.text("status") == "available") ProxmoxStorageStatus.AVAILABLE else ProxmoxStorageStatus.UNKNOWN,
            shared = row.flag("shared") ?: false,
            content = row.text("content")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            usedBytes = row.long("disk"),
            totalBytes = row.long("maxdisk")
        )
    }

    private fun defaultName(kind: ProxmoxGuestKind, vmid: Int) = when (kind) {
        ProxmoxGuestKind.QEMU -> "VM $vmid"
        ProxmoxGuestKind.LXC -> "CT $vmid"
    }

    private fun JsonNode.agentResult(): JsonNode = if (has("result")) path("result") else this

    private fun List<JsonNode>.addressesOfType(type: String): List<String> =
        filter { it.text("ip-address-type") == type }.mapNotNull { it.text("ip-address") }

    private fun booleanOption(value: String): Boolean? = when (value.lowercase()) {
        "1", "yes", "on", "true" -> true
        "0", "no", "off", "false" -> false
        else -> null
    }

    private fun JsonNode.text(field: String): String? = get(field)?.takeIf { !it.isNull && !it.isContainerNode }?.asText()

    private fun JsonNode.number(): Double? = when {
        isNumber -> asDouble()
        isTextual -> asText().trim().toDoubleOrNull()
        else -> null
    }

    private fun JsonNode.number(field: String): Double? = get(field)?.number()

    private fun JsonNode.long(field: String): Long? = number(field)?.toLong()

    private fun JsonNode.flag(field: String): Boolean? {
        val value = get(field) ?: return null
        return when {
            value.isBoolean -> value.asBoolean()
            value.isNumber -> value.asInt() != 0
            value.isTextual -> booleanOption(value.asText())
            else -> null
        }
    }
}
