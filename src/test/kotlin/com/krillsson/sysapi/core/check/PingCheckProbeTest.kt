package com.krillsson.sysapi.core.check

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test

class PingCheckProbeTest {

    private val probe = PingCheckProbe()

    @Test
    fun `fails with the reason when the host cannot be resolved`() {
        // Given
        val spec = pingCheckSpec(host = "no-such-host.invalid", timeoutSeconds = 2)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe false
        result.checkType shouldBe CheckType.PING
        result.responseCode shouldBe null
        result.message.shouldNotBeBlank()
    }
}
