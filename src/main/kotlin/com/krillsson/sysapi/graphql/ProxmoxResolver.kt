package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxNode
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.ProxmoxStorage
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant

@Controller
@SchemaMapping(typeName = "ProxmoxInfoAvailable")
class ProxmoxResolver(
    private val proxmoxService: ProxmoxService
) {

    @SchemaMapping
    fun cluster(): ProxmoxCluster? = proxmoxService.snapshot()?.cluster

    @SchemaMapping
    fun nodes(): List<ProxmoxNode> = proxmoxService.nodes()

    @SchemaMapping
    fun nodeByName(@Argument name: String): ProxmoxNode? = proxmoxService.node(name)

    @SchemaMapping
    fun guests(): List<ProxmoxGuest> = proxmoxService.guests()

    @SchemaMapping
    fun runningGuests(): List<ProxmoxGuest> = proxmoxService.guests().filter { it.status == ProxmoxGuestStatus.RUNNING }

    @SchemaMapping
    fun guest(@Argument id: String): ProxmoxGuest? = proxmoxService.guest(id)

    @SchemaMapping
    fun storages(): List<ProxmoxStorage> = proxmoxService.storages()

    @SchemaMapping
    fun warnings(): List<String> = proxmoxService.snapshot()?.warnings.orEmpty()

    @SchemaMapping
    fun lastSuccessfulPoll(): Instant? = proxmoxService.snapshot()?.lastSuccessfulPoll

    @SchemaMapping
    fun pveVersion(): String? = proxmoxService.snapshot()?.pveVersion
}
