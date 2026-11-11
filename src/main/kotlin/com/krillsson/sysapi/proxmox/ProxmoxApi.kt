package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

object ProxmoxApi {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Version(
        @JsonProperty("version")
        val version: String?,
        @JsonProperty("release")
        val release: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Resource(
        @JsonProperty("type")
        val type: String?,
        @JsonProperty("node")
        val node: String?,
        @JsonProperty("status")
        val status: String?,
        @JsonProperty("vmid")
        val vmid: Int?,
        @JsonProperty("name")
        val name: String?,
        @JsonProperty("template")
        val template: Boolean?,
        @JsonProperty("tags")
        val tags: String?,
        @JsonProperty("hastate")
        val haState: String?,
        @JsonProperty("lock")
        val lock: String?,
        @JsonProperty("uptime")
        val uptime: Long?,
        @JsonProperty("cpu")
        val cpu: Double?,
        @JsonProperty("maxcpu")
        val maxCpu: Double?,
        @JsonProperty("mem")
        val mem: Long?,
        @JsonProperty("maxmem")
        val maxMem: Long?,
        @JsonProperty("memhost")
        val memHost: Long?,
        @JsonProperty("disk")
        val disk: Long?,
        @JsonProperty("maxdisk")
        val maxDisk: Long?,
        @JsonProperty("netin")
        val netIn: Long?,
        @JsonProperty("netout")
        val netOut: Long?,
        @JsonProperty("diskread")
        val diskRead: Long?,
        @JsonProperty("diskwrite")
        val diskWrite: Long?,
        @JsonProperty("storage")
        val storage: String?,
        @JsonProperty("plugintype")
        val pluginType: String?,
        @JsonProperty("shared")
        val shared: Boolean?,
        @JsonProperty("content")
        val content: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ClusterStatusEntry(
        @JsonProperty("type")
        val type: String?,
        @JsonProperty("name")
        val name: String?,
        @JsonProperty("ip")
        val ip: String?,
        @JsonProperty("local")
        val local: Boolean?,
        @JsonProperty("quorate")
        val quorate: Boolean?,
        @JsonProperty("nodes")
        val nodes: Int?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class NodeStatus(
        @JsonProperty("cpuinfo")
        val cpuInfo: CpuInfo?,
        @JsonProperty("ksm")
        val ksm: Ksm?,
        @JsonProperty("loadavg")
        val loadAverage: List<Double>?,
        @JsonProperty("rootfs")
        val rootFs: RootFs?,
        @JsonProperty("pveversion")
        val pveVersion: String?,
        @JsonProperty("kversion")
        val kernelVersion: String?,
        @JsonProperty("current-kernel")
        val currentKernel: CurrentKernel?
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class CpuInfo(
            @JsonProperty("model")
            val model: String?,
            @JsonProperty("cpus")
            val cpus: Int?
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Ksm(
            @JsonProperty("shared")
            val shared: Long?
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class RootFs(
            @JsonProperty("used")
            val used: Long?,
            @JsonProperty("total")
            val total: Long?
        )

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class CurrentKernel(
            @JsonProperty("release")
            val release: String?
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GuestStatus(
        @JsonProperty("status")
        val status: String?,
        @JsonProperty("qmpstatus")
        val qmpStatus: String?,
        @JsonProperty("agent")
        val agent: Boolean?,
        @JsonProperty("swap")
        val swap: Long?,
        @JsonProperty("maxswap")
        val maxSwap: Long?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GuestConfig(
        @JsonProperty("agent")
        val agent: String?,
        @JsonProperty("ostype")
        val osType: String?,
        @JsonProperty("hostname")
        val hostname: String?,
        @JsonProperty("unprivileged")
        val unprivileged: Boolean?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AgentInterfaces(
        @JsonProperty("result")
        val result: List<AgentInterface>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AgentInterface(
        @JsonProperty("name")
        val name: String?,
        @JsonProperty("hardware-address")
        val hardwareAddress: String?,
        @JsonProperty("ip-addresses")
        val ipAddresses: List<IpAddress>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AgentHostName(
        @JsonProperty("result")
        val result: HostName?
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class HostName(
            @JsonProperty("host-name")
            val hostName: String?
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LxcInterface(
        @JsonProperty("name")
        val name: String?,
        @JsonProperty("hwaddr")
        val hwaddr: String?,
        @JsonProperty("hardware-address")
        val hardwareAddress: String?,
        @JsonProperty("inet")
        val inet: String?,
        @JsonProperty("inet6")
        val inet6: String?,
        @JsonProperty("ip-addresses")
        val ipAddresses: List<IpAddress>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class IpAddress(
        @JsonProperty("ip-address")
        val ipAddress: String?,
        @JsonProperty("ip-address-type")
        val ipAddressType: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TaskStatus(
        @JsonProperty("status")
        val status: String?,
        @JsonProperty("exitstatus")
        val exitStatus: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TaskLogLine(
        @JsonProperty("n")
        val n: Int?,
        @JsonProperty("t")
        val t: String?
    )
}
