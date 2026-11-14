package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.monitoring.monitors.UnraidVmRunningMonitor
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.guest
import com.krillsson.sysapi.unraid.HOME_ASSISTANT_UUID
import com.krillsson.sysapi.unraid.UnraidVm
import com.krillsson.sysapi.unraid.UnraidVmService
import com.krillsson.sysapi.unraid.UnraidVmState
import com.krillsson.sysapi.unraid.WINDOWS_UUID
import com.krillsson.sysapi.unraid.unraidVm
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.util.UUID

class MonitorInputCreatorTest {

    private val proxmoxService = mockk<ProxmoxService>()
    private val unraidVmService = mockk<UnraidVmService>()
    private val creator = MonitorInputCreator(
        metrics = mockk(),
        containerService = mockk(),
        checkService = mockk(),
        byteFormatter = mockk(),
        upsService = mockk(),
        containerUpdateChecker = mockk(),
        storagePoolService = mockk(),
        proxmoxService = proxmoxService,
        unraidVmService = unraidVmService
    )

    private fun inputCreator() = MonitorInputCreator(
        metrics = mockk(relaxed = true),
        containerService = mockk(relaxed = true),
        checkService = mockk(relaxed = true),
        byteFormatter = mockk(),
        upsService = mockk(relaxed = true),
        containerUpdateChecker = mockk(relaxed = true),
        storagePoolService = mockk(relaxed = true),
        proxmoxService = proxmoxService,
        unraidVmService = unraidVmService
    )

    private val unraidVmMonitor = UnraidVmRunningMonitor(
        UUID.randomUUID(),
        MonitorConfig(WINDOWS_UUID, true.toConditionalValue(), Duration.ZERO)
    )

    @ParameterizedTest
    @EnumSource(value = Monitor.Type::class, names = ["PROXMOX_GUEST_RUNNING", "PROXMOX_GUEST_CPU_LOAD", "PROXMOX_GUEST_MEMORY_USED"])
    fun `offers proxmox guests to monitor but not templates`(type: Monitor.Type) {
        // Given
        every { proxmoxService.guests() } returns listOf(guest(100), guest(9000, status = ProxmoxGuestStatus.STOPPED, template = true))

        // When
        val items = creator.getMonitorableItemForType(type)

        // Then
        items.map { it.id } shouldContainExactly listOf("100")
    }

    @Test
    fun `reads the value each proxmox guest monitor watches`() {
        // Given
        every { proxmoxService.guests() } returns listOf(guest(100))

        // When
        val running = creator.getMonitorableItemForType(Monitor.Type.PROXMOX_GUEST_RUNNING).single()
        val cpu = creator.getMonitorableItemForType(Monitor.Type.PROXMOX_GUEST_CPU_LOAD).single()
        val memory = creator.getMonitorableItemForType(Monitor.Type.PROXMOX_GUEST_MEMORY_USED).single()

        // Then
        (running.maxValue to running.currentValue) shouldBe (true.toConditionalValue() to true.toConditionalValue())
        (cpu.maxValue to cpu.currentValue) shouldBe (100f.toFractionalValue() to 25f.toFractionalValue())
        (memory.maxValue to memory.currentValue) shouldBe (1024L.toNumericalValue() to 512L.toNumericalValue())
    }

    @Test
    fun `offers unraid vms to monitor with whether they run`() {
        // Given
        every { unraidVmService.vms() } returns listOf(
            unraidVm(HOME_ASSISTANT_UUID, "home-assistant", UnraidVmState.PAUSED),
            unraidVm(WINDOWS_UUID, "Windows 11", UnraidVmState.RUNNING)
        )

        // When
        val items = creator.getMonitorableItemForType(Monitor.Type.UNRAID_VM_RUNNING)

        // Then
        items.map { Triple(it.id, it.name, it.currentValue) } shouldContainExactly listOf(
            Triple(HOME_ASSISTANT_UUID, "home-assistant", false.toConditionalValue()),
            Triple(WINDOWS_UUID, "Windows 11", true.toConditionalValue())
        )
    }

    @Test
    fun `hands the unraid vms to an unraid monitor, and none while they could not be read`() {
        // Given
        val vms = listOf(unraidVm())
        every { unraidVmService.monitorableVms() } returnsMany listOf<List<UnraidVm>?>(vms, null)
        val creator = inputCreator()

        // When
        val readable = creator.createInput(listOf(unraidVmMonitor)).unraidVms
        val unreadable = creator.createInput(listOf(unraidVmMonitor)).unraidVms

        // Then
        readable shouldBe vms
        unreadable.shouldBeNull()
    }

    @Test
    fun `does not ask unraid for vms without an unraid monitor`() {
        // When
        val vms = inputCreator().createInput(emptyList()).unraidVms

        // Then
        vms.shouldBeNull()
        verify(exactly = 0) { unraidVmService.monitorableVms() }
    }
}
