package com.krillsson.sysapi.bash

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class JournalCtlTest {

    @ParameterizedTest
    @ValueSource(strings = ["x;id>/tmp/pwned;true", "x' ; id ; '", "\$(id)", "x --follow", "x\"; id; \""])
    fun `keeps the service name in a single argument`(serviceName: String) {
        // Given
        val since = Instant.parse("2026-11-01T10:00:00Z")

        // When
        val commands = listOf(
            JournalCtl.entriesCommand(serviceName),
            JournalCtl.tailCommand(serviceName),
            JournalCtl.linesCommand(serviceName, limit = 5, since = null, until = null),
            JournalCtl.linesCommand(serviceName, limit = null, since = since, until = since)
        )

        // Then
        commands.forEach { it shouldContain "UNIT=$serviceName" }
    }

    @Test
    fun `tails from the end of the journal`() {
        // When
        val command = JournalCtl.tailCommand("ssh.service")

        // Then
        command shouldBe listOf("journalctl", "--output=json", "UNIT=ssh.service", "-n", "0", "--follow")
    }

    @Test
    fun `limits the number of lines when a limit is given`() {
        // When
        val command = JournalCtl.linesCommand("ssh.service", limit = 10, since = Instant.now(), until = null)

        // Then
        command shouldBe listOf("journalctl", "--output=json", "UNIT=ssh.service", "-n", "10")
    }

    @Test
    fun `passes each time filter as an option and a value`() {
        // Given
        val since = Instant.parse("2026-11-01T10:00:00Z")
        val until = Instant.parse("2026-11-01T11:00:00Z")

        // When
        val command = JournalCtl.linesCommand("ssh.service", limit = -1, since = since, until = until)

        // Then
        command.size shouldBe 7
        command[3] shouldBe "--since"
        command[5] shouldBe "--until"
    }

    @Test
    fun `reads the whole journal for the unit without a limit or time filter`() {
        // When
        val command = JournalCtl.linesCommand("ssh.service", limit = null, since = null, until = null)

        // Then
        command shouldBe listOf("journalctl", "--output=json", "UNIT=ssh.service")
    }
}
