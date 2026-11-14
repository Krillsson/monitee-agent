package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.docker.container
import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WorkloadTypeResolverConfigTest {

    @Test
    fun `resolves a docker container to DockerContainer rather than the Container interface`() {
        // When
        val typeName = WorkloadTypeResolverConfig.containerTypeName(container("abc", "web"))

        // Then
        typeName shouldBe "DockerContainer"
    }

    @Test
    fun `resolves a proxmox guest by its kind`() {
        // Given
        val vm = guest(100, kind = ProxmoxGuestKind.QEMU)
        val lxc = guest(101, kind = ProxmoxGuestKind.LXC)

        // When
        val vmType = WorkloadTypeResolverConfig.virtualMachineTypeName(vm)
        val containerType = WorkloadTypeResolverConfig.containerTypeName(lxc)

        // Then
        vmType shouldBe "ProxmoxVm"
        containerType shouldBe "ProxmoxContainer"
    }

    @Test
    fun `resolves nothing for a guest of the other kind`() {
        // When
        val lxcAsVm = WorkloadTypeResolverConfig.virtualMachineTypeName(guest(101, kind = ProxmoxGuestKind.LXC))
        val vmAsContainer = WorkloadTypeResolverConfig.containerTypeName(guest(100, kind = ProxmoxGuestKind.QEMU))

        // Then
        lxcAsVm.shouldBeNull()
        vmAsContainer.shouldBeNull()
    }

    @Test
    fun `resolves nothing for a source it does not know, so a missing mapping fails loudly`() {
        // When
        val vm = WorkloadTypeResolverConfig.virtualMachineTypeName("unknown")
        val container = WorkloadTypeResolverConfig.containerTypeName(null)

        // Then
        vm.shouldBeNull()
        container.shouldBeNull()
    }
}
