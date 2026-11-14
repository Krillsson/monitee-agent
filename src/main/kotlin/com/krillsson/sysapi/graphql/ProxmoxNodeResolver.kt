package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.proxmox.ProxmoxNode
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxStorage
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "ProxmoxNode")
class ProxmoxNodeResolver(
    private val proxmoxService: ProxmoxService
) {

    @SchemaMapping
    fun virtualMachines(node: ProxmoxNode): List<ProxmoxGuest> = guestsOn(node, ProxmoxGuestKind.QEMU)

    @SchemaMapping
    fun containers(node: ProxmoxNode): List<ProxmoxGuest> = guestsOn(node, ProxmoxGuestKind.LXC)

    @SchemaMapping
    fun storages(node: ProxmoxNode): List<ProxmoxStorage> = proxmoxService.storages().filter { it.node == node.name }

    private fun guestsOn(node: ProxmoxNode, kind: ProxmoxGuestKind): List<ProxmoxGuest> =
        proxmoxService.guests().filter { it.node == node.name && it.kind == kind }
}
