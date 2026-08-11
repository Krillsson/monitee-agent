package com.krillsson.sysapi.bash

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

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
}
