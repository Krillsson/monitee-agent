package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.proxmox.ProxmoxGuestCommand
import java.time.Instant
import java.util.UUID

enum class ProxmoxGuestCommandJobState {
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class ProxmoxGuestCommandJob(
    val jobId: UUID,
    val guestId: String,
    val command: ProxmoxGuestCommand,
    val upid: String,
    val state: ProxmoxGuestCommandJobState,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val lastEvent: ProxmoxGuestCommandEvent
)
