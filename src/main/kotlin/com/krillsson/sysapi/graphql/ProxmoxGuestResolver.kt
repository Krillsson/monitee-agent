package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJob
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommand
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommandJobs
import com.krillsson.sysapi.proxmox.ProxmoxGuestNetwork
import com.krillsson.sysapi.proxmox.ProxmoxGuestNetworkResult
import com.krillsson.sysapi.proxmox.ProxmoxService
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "ProxmoxGuest")
class ProxmoxGuestResolver(
    private val proxmoxService: ProxmoxService,
    private val proxmoxGuestCommandJobs: ProxmoxGuestCommandJobs
) {

    @SchemaMapping
    fun supportedCommands(guest: ProxmoxGuest): List<ProxmoxGuestCommand> = proxmoxService.supportedCommands(guest)

    @SchemaMapping
    fun commandJob(guest: ProxmoxGuest): ProxmoxGuestCommandJob? = proxmoxGuestCommandJobs.latestFor(guest.id)

    @SchemaMapping
    fun qmpStatus(guest: ProxmoxGuest): String? = proxmoxService.guestDetail(guest)?.qmpStatus

    @SchemaMapping
    fun agentEnabled(guest: ProxmoxGuest): Boolean? = proxmoxService.guestDetail(guest)?.agentEnabled

    @SchemaMapping
    fun osType(guest: ProxmoxGuest): String? = proxmoxService.guestDetail(guest)?.osType

    @SchemaMapping
    fun unprivileged(guest: ProxmoxGuest): Boolean? = proxmoxService.guestDetail(guest)?.unprivileged

    @SchemaMapping
    fun swapUsedBytes(guest: ProxmoxGuest): Long? = proxmoxService.guestDetail(guest)?.swapUsedBytes

    @SchemaMapping
    fun swapTotalBytes(guest: ProxmoxGuest): Long? = proxmoxService.guestDetail(guest)?.swapTotalBytes

    @SchemaMapping
    fun network(guest: ProxmoxGuest): ProxmoxGuestNetwork? =
        (proxmoxService.guestNetwork(guest) as? ProxmoxGuestNetworkResult.Available)?.network

    @SchemaMapping
    fun networkUnavailableReason(guest: ProxmoxGuest): String? =
        (proxmoxService.guestNetwork(guest) as? ProxmoxGuestNetworkResult.Unavailable)?.reason
}
