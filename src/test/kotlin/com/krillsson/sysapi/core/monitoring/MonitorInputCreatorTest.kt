package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class MonitorInputCreatorTest {

    private val proxmoxService = mockk<ProxmoxService>()
    private val creator = MonitorInputCreator(
        metrics = mockk(),
        containerService = mockk(),
        checkService = mockk(),
        byteFormatter = mockk(),
        upsService = mockk(),
        containerUpdateChecker = mockk(),
        storagePoolService = mockk(),
        proxmoxService = proxmoxService
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
}
