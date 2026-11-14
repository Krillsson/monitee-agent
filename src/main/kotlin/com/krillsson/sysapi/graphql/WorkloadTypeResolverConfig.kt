package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.unraid.UnraidVm
import graphql.schema.TypeResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer

@Configuration
class WorkloadTypeResolverConfig {

    companion object {
        fun virtualMachineTypeName(source: Any?): String? = when {
            source is ProxmoxGuest && source.kind == ProxmoxGuestKind.QEMU -> "ProxmoxVm"
            source is UnraidVm -> "UnraidVm"
            else -> null
        }

        fun containerTypeName(source: Any?): String? = when {
            source is Container -> "DockerContainer"
            source is ProxmoxGuest && source.kind == ProxmoxGuestKind.LXC -> "ProxmoxContainer"
            else -> null
        }

        private fun resolver(typeName: (Any?) -> String?) = TypeResolver { env ->
            typeName(env.getObject<Any?>())?.let { env.schema.getObjectType(it) }
        }
    }

    @Bean
    fun workloadRuntimeWiringConfigurer(): RuntimeWiringConfigurer = RuntimeWiringConfigurer { wiring ->
        wiring.type("VirtualMachine") { it.typeResolver(resolver(::virtualMachineTypeName)) }
        wiring.type("Container") { it.typeResolver(resolver(::containerTypeName)) }
    }
}
