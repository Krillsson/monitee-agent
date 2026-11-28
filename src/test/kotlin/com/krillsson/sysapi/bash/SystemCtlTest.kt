package com.krillsson.sysapi.bash

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.systemd.CommandResult
import com.krillsson.sysapi.systemd.SystemDaemonCommand
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SystemCtlTest {

    @Test
    fun `passes the unit to systemctl show as its own argument`() {
        // When
        val command = SystemCtl.serviceDetailsCommand("getty@tty1.service")

        // Then
        command shouldBe listOf("systemctl", "show", "--no-pager", "getty@tty1.service")
    }

    @Test
    fun `turns a service command into the systemctl verb`() {
        // When
        val command = SystemCtl.serviceCommand(SystemDaemonCommand.RESTART, "ssh.service")

        // Then
        command shouldBe listOf("systemctl", "restart", "ssh.service")
    }

    @ParameterizedTest
    @ValueSource(strings = ["a;b", "--root=/tmp", "\$(b)"])
    fun `refuses to run a service command for something that is not a unit name`(serviceName: String) {
        // Given
        val systemCtl = SystemCtl(ObjectMapper())

        // When
        val result = systemCtl.performCommandWithService(serviceName, SystemDaemonCommand.STOP)

        // Then
        result.shouldBeInstanceOf<CommandResult.Failed>()
        result.error.message shouldContain "is not a unit name"
    }

    @Test
    fun `finds no details for something that is not a unit name`() {
        // Given
        val systemCtl = SystemCtl(ObjectMapper())

        // When
        val details = systemCtl.serviceDetails("a;b")

        // Then
        details shouldBe null
    }
}
