package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot

sealed interface ProxmoxInfo
data class ProxmoxInfoAvailable(val snapshot: ProxmoxSnapshot) : ProxmoxInfo

data class ProxmoxInfoUnavailable(
    val reason: String,
    val isDisabled: Boolean
) : ProxmoxInfo

fun ProxmoxService.info(): ProxmoxInfo {
    val snapshot = snapshot()
    val status = status()
    return when {
        snapshot != null -> ProxmoxInfoAvailable(snapshot)

        status is ProxmoxService.Status.Unavailable -> ProxmoxInfoUnavailable(status.reason, isDisabled = false)

        status is ProxmoxService.Status.Disabled -> ProxmoxInfoUnavailable(
            "Proxmox support is currently disabled. You can change this in the configuration.yml",
            isDisabled = true
        )

        else -> ProxmoxInfoUnavailable(
            "Proxmox answered for the first time while this query ran, ask again",
            isDisabled = false
        )
    }
}
