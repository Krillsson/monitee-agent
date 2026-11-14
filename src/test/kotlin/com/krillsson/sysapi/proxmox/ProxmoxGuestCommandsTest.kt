package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommand.FORCE_STOP
import com.krillsson.sysapi.workload.PowerCommand.PAUSE
import com.krillsson.sysapi.workload.PowerCommand.REBOOT
import com.krillsson.sysapi.workload.PowerCommand.RESUME
import com.krillsson.sysapi.workload.PowerCommand.SHUTDOWN
import com.krillsson.sysapi.workload.PowerCommand.START
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProxmoxGuestCommandsTest {

    @Test
    fun `offers only start for a stopped guest`() {
        // Given
        val stopped = guest(100, status = ProxmoxGuestStatus.STOPPED)

        // When
        val supported = ProxmoxGuestCommands.supported(stopped, quorate = true, qmpStatus = null)

        // Then
        supported shouldContainExactly listOf(START)
    }

    @Test
    fun `offers shutdown, force stop, reboot and pause for a running vm`() {
        // Given
        val running = guest(100)

        // When
        val supported = ProxmoxGuestCommands.supported(running, quorate = true, qmpStatus = "running")

        // Then
        supported shouldContainExactly listOf(SHUTDOWN, FORCE_STOP, REBOOT, PAUSE)
    }

    @Test
    fun `offers only resume and force stop for a paused vm`() {
        // Given
        val running = guest(100)

        // When
        val supported = ProxmoxGuestCommands.supported(running, quorate = true, qmpStatus = "paused")

        // Then
        supported shouldContainExactly listOf(FORCE_STOP, RESUME)
    }

    @Test
    fun `never offers pause or resume for a container`() {
        // Given
        val container = guest(101, kind = ProxmoxGuestKind.LXC)

        // When
        val supported = ProxmoxGuestCommands.supported(container, quorate = true, qmpStatus = null)

        // Then
        supported shouldContainExactly listOf(SHUTDOWN, FORCE_STOP, REBOOT)
        ProxmoxGuestCommands.refusal(container, PAUSE, quorate = true, qmpStatus = null) shouldBe
            "Containers cannot be paused or resumed"
    }

    @Test
    fun `treats a vm whose run state is unknown as running`() {
        // Given
        val running = guest(100)

        // When
        val supported = ProxmoxGuestCommands.supported(running, quorate = true, qmpStatus = null)

        // Then
        supported shouldContainExactly listOf(SHUTDOWN, FORCE_STOP, REBOOT, PAUSE)
    }

    @ParameterizedTest
    @CsvSource(
        "template, , true, guest-100 is a template",
        "lock, backup, true, guest-100 is locked by Proxmox (backup)",
        "quorum, , false, The cluster has no quorum",
        "ha, error, true, guest-100 is in HA state error",
        "unknown, , true, 'The state of guest-100 is unknown, its node may be offline'"
    )
    fun `offers nothing when the guest cannot be commanded`(case: String, value: String?, quorate: Boolean, reason: String) {
        // Given
        val base = guest(100, status = if (case == "unknown") ProxmoxGuestStatus.UNKNOWN else ProxmoxGuestStatus.RUNNING)
        val blocked = when (case) {
            "template" -> base.copy(template = true)
            "lock" -> base.copy(lock = value)
            "ha" -> base.copy(haState = value)
            else -> base
        }

        // When
        val supported = ProxmoxGuestCommands.supported(blocked, quorate, qmpStatus = "running")

        // Then
        supported.shouldBeEmpty()
        ProxmoxGuestCommands.refusal(blocked, SHUTDOWN, quorate, qmpStatus = "running") shouldBe reason
    }

    @Test
    fun `offers commands to a guest that ha keeps started`() {
        // Given
        val managed = guest(100).copy(haState = "started")

        // When
        val supported = ProxmoxGuestCommands.supported(managed, quorate = true, qmpStatus = "running")

        // Then
        supported shouldContainExactly listOf(SHUTDOWN, FORCE_STOP, REBOOT, PAUSE)
    }

    @ParameterizedTest
    @CsvSource(
        "START, ''",
        "SHUTDOWN, 'timeout=45,forceStop=0'",
        "FORCE_STOP, ''",
        "REBOOT, 'timeout=45'",
        "PAUSE, ''",
        "RESUME, ''"
    )
    fun `sends the shutdown timeout and never forces a shutdown`(command: PowerCommand, expected: String) {
        // When
        val form = ProxmoxGuestCommands.formFor(command, shutdownTimeoutSeconds = 45)

        // Then
        form.entries.joinToString(",") { "${it.key}=${it.value}" } shouldBe expected
    }

    @ParameterizedTest
    @CsvSource(
        "START, start",
        "SHUTDOWN, shutdown",
        "FORCE_STOP, stop",
        "REBOOT, reboot",
        "PAUSE, suspend",
        "RESUME, resume"
    )
    fun `posts each power command to the proxmox action of its own`(command: PowerCommand, action: String) {
        // When
        val posted = ProxmoxGuestCommands.action(command)

        // Then
        posted shouldBe action
    }
}
