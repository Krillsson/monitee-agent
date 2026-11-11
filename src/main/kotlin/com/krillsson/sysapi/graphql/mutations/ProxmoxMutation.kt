package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.proxmox.ProxmoxGuestCommand
import java.util.UUID

data class PerformProxmoxGuestCommandInput(
    val guestId: String,
    val command: ProxmoxGuestCommand
)

interface PerformProxmoxGuestCommandOutput

data class PerformProxmoxGuestCommandOutputStarted(
    val jobId: UUID,
    val guestId: String,
    val upid: String
) : PerformProxmoxGuestCommandOutput

data class PerformProxmoxGuestCommandOutputFailed(
    val reason: String
) : PerformProxmoxGuestCommandOutput
