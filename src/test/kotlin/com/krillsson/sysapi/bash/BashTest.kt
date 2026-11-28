package com.krillsson.sysapi.bash

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@EnabledOnOs(OS.LINUX, OS.MAC)
class BashTest {

    @Test
    fun `finds a command that is on the path`() {
        // Given
        val command = "sh"

        // When
        val exists = Bash.checkIfCommandExists(command)

        // Then
        exists.getOrNull() shouldBe true
    }

    @Test
    fun `does not find a command that is not there`() {
        // Given
        val command = "definitely-not-a-real-command"

        // When
        val exists = Bash.checkIfCommandExists(command)

        // Then
        exists.getOrNull() shouldBe false
    }

    @ParameterizedTest
    @ValueSource(strings = ["a;b", "a' ; b ; '", "\$(b)", "a | b", "a && b", "a > b"])
    fun `passes arguments to the program without a shell interpreting them`(argument: String) {
        // Given
        val arguments = listOf("printf", "%s", argument)

        // When
        val output = Bash.executeToText(arguments)

        // Then
        output.getOrNull() shouldBe argument
    }

    @Test
    fun `streams arguments to the program without a shell interpreting them`() {
        // Given
        val argument = "a;b"

        // When
        val lines = Bash.executeToTextContinuously(listOf("printf", "%s\\n", argument)).collectList().block()

        // Then
        lines shouldBe listOf(argument)
    }

    @ParameterizedTest
    @ValueSource(strings = ["sh;b", "../sh", "-sh", ""])
    fun `does not look up a command name that is not a plain name`(command: String) {
        // When
        val found = Bash.findOnPath(command, "/bin:/usr/bin")

        // Then
        found shouldBe null
    }

    @Test
    fun `finds an executable in the first path directory that has it`(@TempDir directory: File) {
        // Given
        val first = File(directory, "first").apply { mkdirs() }
        val second = File(directory, "second").apply { mkdirs() }
        File(second, "tool").apply { writeText(""); setExecutable(true) }

        // When
        val found = Bash.findOnPath("tool", listOf(first.path, second.path).joinToString(File.pathSeparator))

        // Then
        found shouldBe File(second, "tool")
    }

    @Test
    fun `skips a file on the path that is not executable`(@TempDir directory: File) {
        // Given
        File(directory, "tool").apply { writeText(""); setExecutable(false) }

        // When
        val found = Bash.findOnPath("tool", directory.path)

        // Then
        found shouldBe null
    }

    @Test
    fun `reports the exit status of the program it runs`() {
        // When
        val status = Bash.executeToExitStatus(listOf("sh", "-c", "exit 3"))

        // Then
        status.getOrNull() shouldBe 3
    }
}
