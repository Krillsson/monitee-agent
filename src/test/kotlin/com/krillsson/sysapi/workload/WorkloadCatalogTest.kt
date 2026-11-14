package com.krillsson.sysapi.workload

import com.krillsson.sysapi.workload.WorkloadKind.CONTAINER
import com.krillsson.sysapi.workload.WorkloadKind.VIRTUAL_MACHINE
import com.krillsson.sysapi.workload.WorkloadPlatform.DOCKER
import com.krillsson.sysapi.workload.WorkloadPlatform.PROXMOX
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.IOException

class WorkloadCatalogTest {

    private val docker = FakeWorkloadSource(DOCKER, setOf(CONTAINER)) {
        WorkloadSource.Reading(
            available(DOCKER),
            listOf(
                workload("web", name = "web", runState = RunState.STOPPED),
                workload("db", name = "db")
            )
        )
    }

    private val proxmox = FakeWorkloadSource(PROXMOX, setOf(VIRTUAL_MACHINE, CONTAINER)) {
        WorkloadSource.Reading(
            available(PROXMOX),
            listOf(
                workload("100", name = "Windows", kind = VIRTUAL_MACHINE, platform = PROXMOX),
                workload("101", name = "adguard", kind = CONTAINER, platform = PROXMOX)
            )
        )
    }

    @Test
    fun `mixes the containers of every platform, running first and then by name ignoring case`() {
        // Given
        val catalog = WorkloadCatalog(listOf(proxmox, docker))

        // When
        val list = catalog.list(CONTAINER)

        // Then
        list.all() shouldContainExactly listOf("PROXMOX:101", "DOCKER:db", "DOCKER:web")
        list.running() shouldContainExactly listOf("PROXMOX:101", "DOCKER:db")
    }

    @Test
    fun `lists proxmox vms as virtual machines and its lxc guests as containers`() {
        // Given
        val catalog = WorkloadCatalog(listOf(docker, proxmox))

        // When
        val virtualMachines = catalog.list(VIRTUAL_MACHINE)
        val containers = catalog.list(CONTAINER)

        // Then
        virtualMachines.all() shouldContainExactly listOf("PROXMOX:100")
        containers.all() shouldContainExactly listOf("PROXMOX:101", "DOCKER:db", "DOCKER:web")
    }

    @Test
    fun `leaves a platform that cannot host the kind out of its platforms`() {
        // Given
        val catalog = WorkloadCatalog(listOf(docker, proxmox))

        // When
        val virtualMachines = catalog.list(VIRTUAL_MACHINE)
        val containers = catalog.list(CONTAINER)

        // Then
        virtualMachines.platforms.map { it.platform } shouldContainExactly listOf(PROXMOX)
        containers.platforms.map { it.platform } shouldContainExactly listOf(DOCKER, PROXMOX)
    }

    @Test
    fun `keeps listing the other platforms when one is unavailable`() {
        // Given
        val unavailable = FakeWorkloadSource(PROXMOX, setOf(VIRTUAL_MACHINE, CONTAINER)) {
            WorkloadSource.Reading(
                PlatformAvailability(PROXMOX, available = false, isDisabled = false, reason = "Connection refused", lastSuccessfulPoll = null),
                emptyList()
            )
        }
        val catalog = WorkloadCatalog(listOf(docker, unavailable))

        // When
        val list = catalog.list(CONTAINER)

        // Then
        list.all() shouldContainExactly listOf("DOCKER:db", "DOCKER:web")
        list.platforms.single { it.platform == PROXMOX }.reason shouldBe "Connection refused"
    }

    @Test
    fun `reports a disabled platform as disabled`() {
        // Given
        val disabled = FakeWorkloadSource(PROXMOX, setOf(VIRTUAL_MACHINE, CONTAINER)) {
            WorkloadSource.Reading(
                PlatformAvailability(PROXMOX, available = false, isDisabled = true, reason = "disabled", lastSuccessfulPoll = null),
                emptyList()
            )
        }
        val catalog = WorkloadCatalog(listOf(disabled))

        // When
        val list = catalog.list(VIRTUAL_MACHINE)

        // Then
        list.all().shouldBeEmpty()
        list.platforms.single().isDisabled shouldBe true
    }

    @Test
    fun `reports a platform whose read fails as unavailable and lists the rest`() {
        // Given
        val failing = FakeWorkloadSource(DOCKER, setOf(CONTAINER)) { throw IOException("docker.sock is gone") }
        val catalog = WorkloadCatalog(listOf(failing, proxmox))

        // When
        val list = catalog.list(CONTAINER)

        // Then
        list.all() shouldContainExactly listOf("PROXMOX:101")
        list.platforms.single { it.platform == DOCKER }.available shouldBe false
        list.platforms.single { it.platform == DOCKER }.reason shouldBe "docker.sock is gone"
    }

    @Test
    fun `finds a workload by platform and id only`() {
        // Given
        val sameId = FakeWorkloadSource(DOCKER, setOf(CONTAINER)) {
            WorkloadSource.Reading(available(DOCKER), listOf(workload("101")))
        }
        val list = WorkloadCatalog(listOf(sameId, proxmox)).list(CONTAINER)

        // When
        val found = list.find(PROXMOX, "101")
        val missing = list.find(DOCKER, "100")

        // Then
        found shouldBe "PROXMOX:101"
        missing.shouldBeNull()
    }
}
