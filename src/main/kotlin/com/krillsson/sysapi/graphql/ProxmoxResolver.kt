package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxInfoAvailable
import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxNode
import com.krillsson.sysapi.proxmox.ProxmoxStorage
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant

@Controller
@SchemaMapping(typeName = "ProxmoxInfoAvailable")
class ProxmoxResolver {

    @SchemaMapping
    fun cluster(info: ProxmoxInfoAvailable): ProxmoxCluster = info.snapshot.cluster

    @SchemaMapping
    fun nodes(info: ProxmoxInfoAvailable): List<ProxmoxNode> = info.snapshot.nodes

    @SchemaMapping
    fun nodeByName(info: ProxmoxInfoAvailable, @Argument name: String): ProxmoxNode? =
        info.snapshot.nodes.firstOrNull { it.name == name }

    @SchemaMapping
    fun storages(info: ProxmoxInfoAvailable): List<ProxmoxStorage> = info.snapshot.storages

    @SchemaMapping
    fun warnings(info: ProxmoxInfoAvailable): List<String> = info.snapshot.warnings

    @SchemaMapping
    fun lastSuccessfulPoll(info: ProxmoxInfoAvailable): Instant = info.snapshot.lastSuccessfulPoll

    @SchemaMapping
    fun pveVersion(info: ProxmoxInfoAvailable): String = info.snapshot.pveVersion
}
