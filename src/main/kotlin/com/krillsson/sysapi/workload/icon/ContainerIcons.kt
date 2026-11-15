package com.krillsson.sysapi.workload.icon

import org.springframework.stereotype.Component

@Component
class ContainerIcons {

    private val index: DashboardIconIndex by lazy {
        val lines = javaClass.getResourceAsStream("/$RESOURCE")!!.bufferedReader().use { it.readLines() }
        DashboardIconIndex.parse(lines)
    }

    fun forDockerImage(image: String): ContainerIcon =
        iconFor(ImageNames.dockerCandidates(image), DOCKER_FALLBACK)

    fun forLxc(name: String, osType: String?): ContainerIcon =
        iconFor(listOfNotNull(name, osType), LXC_FALLBACK)

    internal fun iconFor(candidates: List<String>, fallback: String): ContainerIcon {
        val match = candidates.firstNotNullOfOrNull { index.find(it) }
        val name = match ?: fallback
        return ContainerIcon(
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
        const val LXC_FALLBACK = "proxmox"
    }
}
