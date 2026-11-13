package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.proxmox.ProxmoxSnapshot

interface ProxmoxInfo
data class ProxmoxInfoAvailable(val snapshot: ProxmoxSnapshot) : ProxmoxInfo

data class ProxmoxInfoUnavailable(
    val reason: String,
    val isDisabled: Boolean
) : ProxmoxInfo
