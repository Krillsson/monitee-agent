package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.proxmox.ProxmoxClient.Result
import javax.net.ssl.SSLException

object ProxmoxConverter {

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

    fun version(version: ProxmoxApi.Version): String = version.version ?: version.release ?: "unknown"

    fun resources(resources: List<ProxmoxApi.Resource>): Resources = Resources(
        nodes = resources.filter { it.type == "node" }.mapNotNull { resourceNode(it) },
        guests = resources.mapNotNull { guest(it) },
        storages = resources.filter { it.type == "storage" }.mapNotNull { storage(it) }
    )

    fun clusterStatus(entries: List<ProxmoxApi.ClusterStatusEntry>): ClusterStatus {
        val nodes = entries.filter { it.type == "node" }
            .mapNotNull { entry -> entry.name?.let { it to ClusterNode(ip = entry.ip, local = entry.local == true) } }
            .toMap()
        val cluster = entries.firstOrNull { it.type == "cluster" }
            ?.let {
                ProxmoxCluster(
                    name = it.name,
                    quorate = it.quorate ?: false,
                    nodeCount = it.nodes ?: nodes.size
                )
            }
            ?: ProxmoxCluster(name = null, quorate = true, nodeCount = nodes.size)
        return ClusterStatus(cluster, nodes)
    }

    fun nodeDetail(status: ProxmoxApi.NodeStatus): NodeDetail = NodeDetail(
        cpuModel = status.cpuInfo?.model,
        cpuCount = status.cpuInfo?.cpus,
        ksmSharedBytes = status.ksm?.shared,
        loadAverages = status.loadAverage,
        rootFsUsedBytes = status.rootFs?.used,
        rootFsTotalBytes = status.rootFs?.total,
        pveVersion = status.pveVersion,
        kernelRelease = status.currentKernel?.release ?: status.kernelVersion
    )

    fun node(
        resource: ResourceNode,
        member: ClusterNode?,
        detail: NodeDetail?,
        detailUnavailableReason: String?
    ): ProxmoxNode = ProxmoxNode(
        name = resource.name,
        status = resource.status,
        local = member?.local ?: false,
        ip = member?.ip,
        uptimeSeconds = resource.uptimeSeconds,
        cpuUsagePercent = resource.cpuUsagePercent,
        cpuCount = resource.cpuCount ?: detail?.cpuCount,
        cpuModel = detail?.cpuModel,
        memoryUsedBytes = resource.memoryUsedBytes,
        memoryTotalBytes = resource.memoryTotalBytes,
        ksmSharedBytes = detail?.ksmSharedBytes,
        loadAverages = detail?.loadAverages,
        rootFsUsedBytes = detail?.rootFsUsedBytes,
        rootFsTotalBytes = detail?.rootFsTotalBytes,
        pveVersion = detail?.pveVersion,
        kernelRelease = detail?.kernelRelease,
        detailUnavailableReason = detailUnavailableReason
    )

    fun guestDetail(
        kind: ProxmoxGuestKind,
        status: ProxmoxApi.GuestStatus,
        config: ProxmoxApi.GuestConfig
    ): ProxmoxGuestDetail = when (kind) {
        ProxmoxGuestKind.QEMU -> ProxmoxGuestDetail(
            qmpStatus = status.qmpStatus ?: status.status,
            agentEnabled = config.agent?.let { agentEnabled(it) } ?: status.agent ?: false,
            osType = config.osType,
            hostname = null,
            unprivileged = null,
            swapUsedBytes = null,
            swapTotalBytes = null
        )

        ProxmoxGuestKind.LXC -> ProxmoxGuestDetail(
            qmpStatus = null,
            agentEnabled = null,
            osType = config.osType,
            hostname = config.hostname,
            unprivileged = config.unprivileged ?: false,
            swapUsedBytes = status.swap,
            swapTotalBytes = status.maxSwap
        )
    }

    fun qemuNetwork(interfaces: ProxmoxApi.AgentInterfaces, hostName: ProxmoxApi.AgentHostName?): ProxmoxGuestNetwork =
        ProxmoxGuestNetwork(
            hostname = hostName?.result?.hostName,
            interfaces = interfaces.result.orEmpty()
                .filter { it.name != LOOPBACK }
                .mapNotNull { networkInterface ->
                    val name = networkInterface.name ?: return@mapNotNull null
                    val addresses = networkInterface.ipAddresses.orEmpty()
                    ProxmoxGuestNetworkInterface(
                        name = name,
                        macAddress = networkInterface.hardwareAddress,
                        ipv4 = addresses.ofType("ipv4"),
                        ipv6 = addresses.ofType("ipv6")
                    )
                }
        )

    fun lxcNetwork(interfaces: List<ProxmoxApi.LxcInterface>, hostname: String?): ProxmoxGuestNetwork = ProxmoxGuestNetwork(
        hostname = hostname,
        interfaces = interfaces
            .filter { it.name != LOOPBACK }
            .mapNotNull { networkInterface ->
                val name = networkInterface.name ?: return@mapNotNull null
                val addresses = networkInterface.ipAddresses.orEmpty()
                ProxmoxGuestNetworkInterface(
                    name = name,
                    macAddress = networkInterface.hwaddr ?: networkInterface.hardwareAddress,
                    ipv4 = addresses.ofType("inet")
                        .ifEmpty { listOfNotNull(networkInterface.inet?.substringBefore('/')) },
                    ipv6 = addresses.ofType("inet6")
                        .ifEmpty { listOfNotNull(networkInterface.inet6?.substringBefore('/')) }
                )
            }
    )

    fun agentEnabled(value: String): Boolean {
        val options = value.split(',').map { it.trim() }
        val enabled = options.firstOrNull { !it.contains('=') }
            ?: options.firstOrNull { it.startsWith("enabled=") }?.substringAfter('=')
        return when (enabled?.lowercase()) {
            "1", "yes", "on", "true" -> true
            else -> false
        }
    }

    fun failureReason(result: Result<*>, host: String, port: Int): String = when (result) {
        is Result.Ok -> "OK"
        is Result.Unauthorized ->
            "Proxmox rejected the API token (${result.reason}). Check proxmox.tokenId and proxmox.secret, and whether the token has expired"

        is Result.Forbidden -> "Proxmox denied access: ${result.reason}"
        is Result.Failed -> "Proxmox answered ${result.status}: ${result.reason}"
        is Result.TransportFailure -> when (result.error) {
            is SSLException ->
                "The certificate of $host:$port is not trusted (${result.error.message}). Set proxmox.caCertificatePath to a copy of /etc/pve/pve-root-ca.pem and proxmox.host to the node's name, or set proxmox.verifyTls to false"

            else -> "Could not reach Proxmox at $host:$port: ${result.error.message ?: result.error::class.java.simpleName}"
        }
    }

    private fun resourceNode(resource: ProxmoxApi.Resource): ResourceNode? {
        val name = resource.node ?: return null
        return ResourceNode(
            name = name,
            status = when (resource.status) {
                "online" -> ProxmoxNodeStatus.ONLINE
                "offline" -> ProxmoxNodeStatus.OFFLINE
                else -> ProxmoxNodeStatus.UNKNOWN
            },
            uptimeSeconds = resource.uptime,
            cpuUsagePercent = resource.cpu?.let { it * 100 },
            cpuCount = resource.maxCpu?.toInt(),
            memoryUsedBytes = resource.mem,
            memoryTotalBytes = resource.maxMem
        )
    }

    private fun guest(resource: ProxmoxApi.Resource): ProxmoxGuest? {
        val kind = when (resource.type) {
            "qemu" -> ProxmoxGuestKind.QEMU
            "lxc" -> ProxmoxGuestKind.LXC
            else -> return null
        }
        val vmid = resource.vmid ?: return null
        val node = resource.node ?: return null
        val id = vmid.toString()
        val status = when (resource.status) {
            "running" -> ProxmoxGuestStatus.RUNNING
            "stopped" -> ProxmoxGuestStatus.STOPPED
            else -> ProxmoxGuestStatus.UNKNOWN
        }
        val memoryTotal = resource.maxMem ?: 0
        return ProxmoxGuest(
            id = id,
            vmid = vmid,
            kind = kind,
            name = resource.name?.takeIf { it.isNotBlank() } ?: defaultName(kind, vmid),
            node = node,
            status = status,
            template = resource.template ?: false,
            tags = resource.tags?.split(tagSeparators)?.filter { it.isNotBlank() }.orEmpty(),
            haState = resource.haState,
            lock = resource.lock?.takeIf { it.isNotBlank() },
            uptimeSeconds = resource.uptime ?: 0,
            cpuCount = resource.maxCpu?.toInt() ?: 0,
            memoryTotalBytes = memoryTotal,
            metrics = if (status == ProxmoxGuestStatus.RUNNING) guestMetrics(id, kind, resource, memoryTotal) else null
        )
    }

    private fun guestMetrics(
        id: String,
        kind: ProxmoxGuestKind,
        resource: ProxmoxApi.Resource,
        memoryTotal: Long
    ): ProxmoxGuestMetrics {
        val memoryUsed = resource.mem ?: 0
        return ProxmoxGuestMetrics(
            id = id,
            cpuUsage = ProxmoxGuestCpuUsage(usagePercentTotal = (resource.cpu ?: 0.0) * 100),
            memoryUsage = ProxmoxGuestMemoryUsage(
                usageBytes = memoryUsed,
                usagePercent = if (memoryTotal > 0) memoryUsed * 100.0 / memoryTotal else 0.0,
                limitBytes = memoryTotal,
                hostUsageBytes = resource.memHost
            ),
            networkUsage = ProxmoxGuestNetworkUsage(
                bytesReceived = resource.netIn ?: 0,
                bytesTransferred = resource.netOut ?: 0
            ),
            blockIOUsage = ProxmoxGuestBlockIOUsage(
                bytesRead = resource.diskRead ?: 0,
                bytesWritten = resource.diskWrite ?: 0
            ),
            diskUsage = when (kind) {
                ProxmoxGuestKind.QEMU -> null
                ProxmoxGuestKind.LXC -> ProxmoxGuestDiskUsage(
                    usageBytes = resource.disk ?: 0,
                    limitBytes = resource.maxDisk ?: 0
                )
            }
        )
    }

    private fun storage(resource: ProxmoxApi.Resource): ProxmoxStorage? {
        val node = resource.node ?: return null
        val storage = resource.storage ?: return null
        return ProxmoxStorage(
            id = "$node/$storage",
            storage = storage,
            node = node,
            type = resource.pluginType,
            status = if (resource.status == "available") ProxmoxStorageStatus.AVAILABLE else ProxmoxStorageStatus.UNKNOWN,
            shared = resource.shared ?: false,
            content = resource.content?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            usedBytes = resource.disk,
            totalBytes = resource.maxDisk
        )
    }

    private fun defaultName(kind: ProxmoxGuestKind, vmid: Int) = when (kind) {
        ProxmoxGuestKind.QEMU -> "VM $vmid"
        ProxmoxGuestKind.LXC -> "CT $vmid"
    }

    private fun List<ProxmoxApi.IpAddress>.ofType(type: String): List<String> =
        filter { it.ipAddressType == type }.mapNotNull { it.ipAddress }
}
