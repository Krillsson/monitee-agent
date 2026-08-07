package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.stereotype.Component

/**
 * Matching is on the interface name rather than the address range, because the range says very
 * little: Docker's default pool sits inside 172.16.0.0/12, which is also where a lot of real
 * networks live, and the pool is configurable anyway. The names are not — Docker derives
 * `br-<network id>` from the network it created and `veth*` from the container end of the pair,
 * and neither ever belongs to anything else. `br0` and friends are deliberately not matched:
 * those are the host's own bridges, which on Unraid is where the LAN address lives.
 */
@Component
class ContainerNetworkInterfaces(configFile: YAMLConfigFile) {

    private val hidden = configFile.docker.hideContainerNetworks

    fun isContainerNetwork(name: String) = hidden && patterns.any { it.matches(name) }

    fun <T> visible(interfaces: List<T>, name: (T) -> String) =
        if (hidden) interfaces.filterNot { isContainerNetwork(name(it)) } else interfaces

    companion object {
        private val patterns = listOf(
            Regex("docker[0-9]+"),
            Regex("docker_gwbridge"),
            Regex("br-[0-9a-f]{12}"),
            Regex("veth[0-9a-zA-Z]+"),
            Regex("podman[0-9]*"),
            Regex("cni-podman[0-9]+")
        )
    }
}
