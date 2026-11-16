package com.krillsson.sysapi.workload.icon

import org.springframework.stereotype.Component

@Component
class WorkloadIcons {

    private val index: DashboardIconIndex by lazy {
        val lines = javaClass.getResourceAsStream("/$RESOURCE")!!.bufferedReader().use { it.readLines() }
        DashboardIconIndex.parse(lines)
    }

    fun forDockerImage(image: String): WorkloadIcon =
        iconFor(IconCandidates.dockerCandidates(image).asSequence(), DOCKER_FALLBACK)

    fun forLxc(name: String, osType: () -> String?): WorkloadIcon =
        iconFor(
            IconCandidates.nameCandidates(name).asSequence() + sequence { yieldAll(IconCandidates.lxcOsTypeCandidates(osType())) },
            PROXMOX_FALLBACK
        )

    fun forProxmoxVm(name: String, osType: () -> String?): WorkloadIcon =
        iconFor(
            IconCandidates.nameCandidates(name).asSequence() + sequence { yieldAll(IconCandidates.qemuOsTypeCandidates(osType())) },
            PROXMOX_FALLBACK
        )

    fun forUnraidVm(name: String): WorkloadIcon =
        iconFor(IconCandidates.nameCandidates(name).asSequence(), UNRAID_FALLBACK)

    internal fun iconFor(candidates: Sequence<String>, fallback: String): WorkloadIcon {
        val match = candidates.firstNotNullOfOrNull { index.find(it) }
        val name = match ?: fallback
        return WorkloadIcon(
            name = name,
            isFallback = match == null,
            svg = link(name, "svg"),
            png = link(name, "png"),
            webp = link(name, "webp")
        )
    }

    private fun link(name: String, format: String) =
        "$CDN/$format/$name.$format".takeIf { format in index.formats(name) }

    companion object {
        const val RESOURCE = "dashboard-icons.txt"
        const val CDN = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons"
        const val DOCKER_FALLBACK = "docker"
        const val PROXMOX_FALLBACK = "proxmox"
        const val UNRAID_FALLBACK = "unraid"
    }
}
