package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJob
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommand
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommandJobs
import com.krillsson.sysapi.proxmox.ProxmoxGuestNetwork
import com.krillsson.sysapi.proxmox.ProxmoxService
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "ProxmoxVm")
class ProxmoxVmResolver(
    private val proxmoxService: ProxmoxService,
    private val proxmoxGuestCommandJobs: ProxmoxGuestCommandJobs
) {

    @SchemaMapping
    fun supportedCommands(guest: ProxmoxGuest): List<ProxmoxGuestCommand> = proxmoxService.listedCommands(guest)

    @SchemaMapping
    fun commandJob(guest: ProxmoxGuest): ProxmoxGuestCommandJob? = proxmoxGuestCommandJobs.latestFor(guest.id)

    @SchemaMapping
    fun qmpStatus(guest: ProxmoxGuest): String? = proxmoxService.guestDetail(guest)?.qmpStatus

    @SchemaMapping
    fun agentEnabled(guest: ProxmoxGuest): Boolean? = proxmoxService.guestDetail(guest)?.agentEnabled

    @SchemaMapping
    fun osType(guest: ProxmoxGuest): String? = proxmoxService.guestDetail(guest)?.osType

    @SchemaMapping
    fun network(guest: ProxmoxGuest): ProxmoxGuestNetwork? = proxmoxService.availableNetwork(guest)

    @SchemaMapping
    fun networkUnavailableReason(guest: ProxmoxGuest): String? = proxmoxService.networkUnavailableReason(guest)
}
