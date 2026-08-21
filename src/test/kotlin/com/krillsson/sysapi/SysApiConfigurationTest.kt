package com.krillsson.sysapi

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import oshi.SystemInfo

class SysApiConfigurationTest {

    @Test
    fun `falls back to the JNA backend when the FFM backend cannot be loaded on this runtime`() {
        // Given
        val configuration = SysApiConfiguration()

        // When
        val provider = configuration.systemInfo()

        // Then
        val expectFfm = Runtime.version().feature() >= 25
        (provider !is SystemInfo) shouldBe expectFfm
    }
}
