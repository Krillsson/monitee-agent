package com.krillsson.sysapi.graphql.domain

import java.time.Instant
import java.util.UUID

interface ProxmoxGuestCommandEvent {
    val jobId: UUID
    val guestId: String
    val timestamp: Instant
}

data class ProxmoxGuestCommandRunning(
    override val jobId: UUID,
    override val guestId: String,
    override val timestamp: Instant,
    val upid: String
) : ProxmoxGuestCommandEvent

data class ProxmoxGuestCommandSucceeded(
    override val jobId: UUID,
    override val guestId: String,
    override val timestamp: Instant,
    val upid: String,
    val exitStatus: String
) : ProxmoxGuestCommandEvent

data class ProxmoxGuestCommandFailed(
    override val jobId: UUID,
    override val guestId: String,
    override val timestamp: Instant,
    val upid: String,
    val reason: String,
    val log: List<String>,
    val canForceStop: Boolean
) : ProxmoxGuestCommandEvent
