package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJob
import com.krillsson.sysapi.graphql.domain.ProxmoxInfoAvailable
import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommandJobs
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxNode
import com.krillsson.sysapi.proxmox.ProxmoxStorage
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.util.UUID

@Controller
@SchemaMapping(typeName = "ProxmoxInfoAvailable")
class ProxmoxResolver(
    private val proxmoxGuestCommandJobs: ProxmoxGuestCommandJobs
) {

    @SchemaMapping
    fun cluster(info: ProxmoxInfoAvailable): ProxmoxCluster = info.snapshot.cluster

    @SchemaMapping
    fun nodes(info: ProxmoxInfoAvailable): List<ProxmoxNode> = info.snapshot.nodes

    @SchemaMapping
    fun nodeByName(info: ProxmoxInfoAvailable, @Argument name: String): ProxmoxNode? =
        info.snapshot.nodes.firstOrNull { it.name == name }

    @SchemaMapping
    fun guests(info: ProxmoxInfoAvailable): List<ProxmoxGuest> = info.snapshot.guests

    @SchemaMapping
    fun runningGuests(info: ProxmoxInfoAvailable): List<ProxmoxGuest> =
        info.snapshot.guests.filter { it.status == ProxmoxGuestStatus.RUNNING }

    @SchemaMapping
    fun guest(info: ProxmoxInfoAvailable, @Argument id: String): ProxmoxGuest? =
        info.snapshot.guests.firstOrNull { it.id == id }

    @SchemaMapping
    fun storages(info: ProxmoxInfoAvailable): List<ProxmoxStorage> = info.snapshot.storages

    @SchemaMapping
    fun warnings(info: ProxmoxInfoAvailable): List<String> = info.snapshot.warnings

    @SchemaMapping
    fun guestCommandJob(@Argument jobId: UUID?): ProxmoxGuestCommandJob? = when (jobId) {
        null -> proxmoxGuestCommandJobs.current()
        else -> proxmoxGuestCommandJobs.find(jobId)
    }

    @SchemaMapping
    fun lastSuccessfulPoll(info: ProxmoxInfoAvailable): Instant = info.snapshot.lastSuccessfulPoll

    @SchemaMapping
    fun pveVersion(info: ProxmoxInfoAvailable): String = info.snapshot.pveVersion
}
