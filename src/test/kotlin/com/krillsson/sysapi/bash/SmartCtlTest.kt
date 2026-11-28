package com.krillsson.sysapi.bash

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SmartCtlTest {

    @Test
    fun `queries a device without waking it from standby`() {
        // When
        val command = SmartCtl.smartDataCommand("/dev/sda", null)

        // Then
        command shouldBe listOf("smartctl", "-n", "standby", "-jA", "/dev/sda")
    }

    @Test
    fun `splits the configured flags into separate arguments before the device`() {
        // When
        val command = SmartCtl.smartDataCommand("/dev/sdb", "  -d sat,12   -T permissive ")

        // Then
        command shouldBe listOf("smartctl", "-n", "standby", "-d", "sat,12", "-T", "permissive", "-jA", "/dev/sdb")
    }
}
