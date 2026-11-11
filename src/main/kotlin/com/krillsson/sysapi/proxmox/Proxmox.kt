package com.krillsson.sysapi.proxmox

import java.time.Instant

enum class ProxmoxGuestKind(val apiPath: String) {
    QEMU("qemu"),
    LXC("lxc")
}

enum class ProxmoxGuestStatus { RUNNING, STOPPED, UNKNOWN }

enum class ProxmoxNodeStatus { ONLINE, OFFLINE, UNKNOWN }

enum class ProxmoxGuestCommand(val action: String) {
    START("start"),
    SHUTDOWN("shutdown"),
    FORCE_STOP("stop"),
    REBOOT("reboot"),
    SUSPEND("suspend"),
    RESUME("resume")
}

enum class ProxmoxStorageStatus { AVAILABLE, UNKNOWN }

data class ProxmoxCluster(
    val name: String?,
    val quorate: Boolean,
    val nodeCount: Int
)

data class ProxmoxNode(
    val name: String,
    val status: ProxmoxNodeStatus,
    val local: Boolean,
    val ip: String?,
    val uptimeSeconds: Long?,
    val cpuUsagePercent: Double?,
    val cpuCount: Int?,
    val cpuModel: String?,
    val memoryUsedBytes: Long?,
    val memoryTotalBytes: Long?,
    val ksmSharedBytes: Long?,
    val loadAverages: List<Double>?,
    val rootFsUsedBytes: Long?,
    val rootFsTotalBytes: Long?,
    val pveVersion: String?,
    val kernelRelease: String?,
    val detailUnavailableReason: String?
)

data class ProxmoxGuest(
    val id: String,
    val vmid: Int,
    val kind: ProxmoxGuestKind,
    val name: String,
    val node: String,
    val status: ProxmoxGuestStatus,
    val template: Boolean,
    val tags: List<String>,
    val haState: String?,
    val lock: String?,
    val uptimeSeconds: Long,
    val cpuCount: Int,
    val memoryTotalBytes: Long,
    val metrics: ProxmoxGuestMetrics?
) {
    fun apiPath() = "/nodes/$node/${kind.apiPath}/$vmid"
}

data class ProxmoxGuestMetrics(
    val id: String,
    val cpuUsage: ProxmoxGuestCpuUsage,
    val memoryUsage: ProxmoxGuestMemoryUsage,
    val networkUsage: ProxmoxGuestNetworkUsage,
    val blockIOUsage: ProxmoxGuestBlockIOUsage,
    val diskUsage: ProxmoxGuestDiskUsage?
)

data class ProxmoxGuestCpuUsage(val usagePercentTotal: Double)

data class ProxmoxGuestMemoryUsage(
    val usageBytes: Long,
    val usagePercent: Double,
    val limitBytes: Long,
    val hostUsageBytes: Long?
)

data class ProxmoxGuestNetworkUsage(val bytesReceived: Long, val bytesTransferred: Long)

data class ProxmoxGuestBlockIOUsage(val bytesRead: Long, val bytesWritten: Long)

data class ProxmoxGuestDiskUsage(val usageBytes: Long, val limitBytes: Long)

data class ProxmoxGuestDetail(
    val qmpStatus: String?,
    val agentEnabled: Boolean?,
    val osType: String?,
    val hostname: String?,
    val unprivileged: Boolean?,
    val swapUsedBytes: Long?,
    val swapTotalBytes: Long?
)

data class ProxmoxGuestNetwork(
    val hostname: String?,
    val interfaces: List<ProxmoxGuestNetworkInterface>
)

data class ProxmoxGuestNetworkInterface(
    val name: String,
    val macAddress: String?,
    val ipv4: List<String>,
    val ipv6: List<String>
)

sealed interface ProxmoxGuestNetworkResult {
    data class Available(val network: ProxmoxGuestNetwork) : ProxmoxGuestNetworkResult
    data class Unavailable(val reason: String) : ProxmoxGuestNetworkResult
}

data class ProxmoxStorage(
    val id: String,
    val storage: String,
    val node: String,
    val type: String?,
    val status: ProxmoxStorageStatus,
    val shared: Boolean,
    val content: List<String>,
    val usedBytes: Long?,
    val totalBytes: Long?
)

data class ProxmoxSnapshot(
    val cluster: ProxmoxCluster,
    val nodes: List<ProxmoxNode>,
    val guests: List<ProxmoxGuest>,
    val storages: List<ProxmoxStorage>,
    val warnings: List<String>,
    val pveVersion: String,
    val lastSuccessfulPoll: Instant
)
