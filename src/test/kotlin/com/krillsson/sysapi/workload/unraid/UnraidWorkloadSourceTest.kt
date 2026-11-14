package com.krillsson.sysapi.workload.unraid

import com.krillsson.sysapi.unraid.HOME_ASSISTANT_UUID
import com.krillsson.sysapi.unraid.UnraidVmService
import com.krillsson.sysapi.unraid.UnraidVmState
import com.krillsson.sysapi.unraid.WINDOWS_UUID
import com.krillsson.sysapi.unraid.unraidSnapshot
import com.krillsson.sysapi.unraid.unraidVm
import com.krillsson.sysapi.workload.PlatformAvailability
import com.krillsson.sysapi.workload.RunState
import com.krillsson.sysapi.workload.WorkloadKind
import com.krillsson.sysapi.workload.WorkloadPlatform
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant

class UnraidWorkloadSourceTest {

    private val unraidVmService = mockk<UnraidVmService>()
    private val source = UnraidWorkloadSource(unraidVmService)
    private val polledAt = Instant.parse("2026-11-14T10:00:00Z")

    @Test
    fun `lists the vms with the state the app draws`() {
        // Given
        every { unraidVmService.status() } returns UnraidVmService.Status.Available
        every { unraidVmService.snapshot() } returns unraidSnapshot(
            unraidVm(HOME_ASSISTANT_UUID, "home-assistant", UnraidVmState.SHUTDOWN),
            unraidVm(WINDOWS_UUID, "Windows 11", UnraidVmState.IDLE),
            lastSuccessfulPoll = polledAt
        )

        // When
        val reading = source.read()

        // Then
        reading.workloads.map { Triple(it.id, it.kind, it.runState) } shouldContainExactly listOf(
            Triple(HOME_ASSISTANT_UUID, WorkloadKind.VIRTUAL_MACHINE, RunState.STOPPING),
            Triple(WINDOWS_UUID, WorkloadKind.VIRTUAL_MACHINE, RunState.RUNNING)
        )
        reading.availability shouldBe PlatformAvailability(
            WorkloadPlatform.UNRAID,
            available = true,
            isDisabled = false,
            reason = null,
            lastSuccessfulPoll = polledAt
        )
    }

    @Test
    fun `is available with a reason while the vm manager is stopped`() {
        // Given
        every { unraidVmService.status() } returns UnraidVmService.Status.Available
        every { unraidVmService.snapshot() } returns unraidSnapshot(
            vmsUnavailableReason = "VMs are disabled in Unraid (Settings → VM Manager)",
            lastSuccessfulPoll = polledAt
        )

        // When
        val reading = source.read()

        // Then
        reading.workloads.shouldBeEmpty()
        reading.availability.available shouldBe true
        reading.availability.reason shouldBe "VMs are disabled in Unraid (Settings → VM Manager)"
    }

    @Test
    fun `says unraid is disabled`() {
        // Given
        every { unraidVmService.status() } returns UnraidVmService.Status.Disabled
        every { unraidVmService.snapshot() } returns null

        // When
        val reading = source.read()

        // Then
        reading.availability shouldBe PlatformAvailability(
            WorkloadPlatform.UNRAID,
            available = false,
            isDisabled = true,
            reason = "Unraid support is currently disabled. You can change this in the configuration.yml",
            lastSuccessfulPoll = null
        )
    }

    @Test
    fun `passes on why unraid is unavailable`() {
        // Given
        every { unraidVmService.status() } returns UnraidVmService.Status.Unavailable("Could not reach Unraid")
        every { unraidVmService.snapshot() } returns null

        // When
        val reading = source.read()

        // Then
        reading.workloads.shouldBeEmpty()
        reading.availability shouldBe PlatformAvailability(
            WorkloadPlatform.UNRAID,
            available = false,
            isDisabled = false,
            reason = "Could not reach Unraid",
            lastSuccessfulPoll = null
        )
    }
}
