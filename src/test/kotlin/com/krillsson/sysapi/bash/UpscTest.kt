package com.krillsson.sysapi.bash

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UpscTest {

    @ParameterizedTest
    @ValueSource(strings = ["a;b", "a' ; b ; '", "\$(b)", "a | b", "-L"])
    fun `keeps a device name from the server in a single argument`(name: String) {
        // When
        val command = Upsc.queryCommand(name, "localhost", 3493)

        // Then
        command shouldBe listOf("upsc", "$name@localhost:3493")
    }

    @Test
    fun `lists devices on the configured host and port`() {
        // When
        val command = Upsc.listCommand("nas.local", 3493)

        // Then
        command shouldBe listOf("upsc", "-L", "nas.local:3493")
    }

    @Test
    fun `leaves out the port when none is configured`() {
        // When
        val command = Upsc.queryCommand("ups", "nas.local", null)

        // Then
        command shouldBe listOf("upsc", "ups@nas.local")
    }

    @Test
    fun `drops devices whose name the server sent is not a NUT name`() {
        // Given
        val output = """
            ups: APC Back-UPS
            a;b: Not a NUT name
            eaton-5p: Eaton 5P
        """.trimIndent()

        // When
        val devices = Upsc.parseDeviceList(output)

        // Then
        devices.map { it.name } shouldBe listOf("ups", "eaton-5p")
        devices.first().description shouldBe "APC Back-UPS"
    }
}
