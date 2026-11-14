package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestNetwork
import com.krillsson.sysapi.proxmox.ProxmoxService
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "ProxmoxContainer")
class ProxmoxContainerResolver(
    private val proxmoxService: ProxmoxService
) {

    @SchemaMapping
    fun osType(guest: ProxmoxGuest): String? = proxmoxService.guestDetail(guest)?.osType

    @SchemaMapping
    fun unprivileged(guest: ProxmoxGuest): Boolean? = proxmoxService.guestDetail(guest)?.unprivileged

    @SchemaMapping
    fun swapUsedBytes(guest: ProxmoxGuest): Long? = proxmoxService.guestDetail(guest)?.swapUsedBytes

    @SchemaMapping
    fun swapTotalBytes(guest: ProxmoxGuest): Long? = proxmoxService.guestDetail(guest)?.swapTotalBytes

    @SchemaMapping
    fun network(guest: ProxmoxGuest): ProxmoxGuestNetwork? = proxmoxService.availableNetwork(guest)

    @SchemaMapping
    fun networkUnavailableReason(guest: ProxmoxGuest): String? = proxmoxService.networkUnavailableReason(guest)
}
