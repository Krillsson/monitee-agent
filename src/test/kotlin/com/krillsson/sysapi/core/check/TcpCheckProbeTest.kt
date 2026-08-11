package com.krillsson.sysapi.core.check

import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket

class TcpCheckProbeTest {

    private val probe = TcpCheckProbe()

    @Test
    fun `passes when the port accepts a connection`() {
        // Given
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val spec = tcpCheckSpec(host = "127.0.0.1", port = server.localPort)

            // When
            val result = probe.probe(spec)

            // Then
            result.successful shouldBe true
            result.checkType shouldBe CheckType.TCP
            result.message shouldContain "127.0.0.1:${server.localPort}"
            result.latencyMs shouldBeGreaterThanOrEqual 0
            result.responseCode shouldBe null
        }
    }

    @Test
    fun `fails with the reason when nothing is listening`() {
        // Given
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val spec = tcpCheckSpec(host = "127.0.0.1", port = port)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe false
        result.message shouldContain "refused"
    }

    @Test
    fun `fails when the host cannot be resolved`() {
        // Given
        val spec = tcpCheckSpec(host = "no-such-host.invalid", port = 80, timeoutSeconds = 2)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe false
        result.checkType shouldBe CheckType.TCP
    }
}
