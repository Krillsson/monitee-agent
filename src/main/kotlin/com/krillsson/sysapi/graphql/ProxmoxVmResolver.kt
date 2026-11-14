package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestNetwork
import com.krillsson.sysapi.proxmox.ProxmoxService
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "ProxmoxVm")
class ProxmoxVmResolver(
    private val proxmoxService: ProxmoxService
) {

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
