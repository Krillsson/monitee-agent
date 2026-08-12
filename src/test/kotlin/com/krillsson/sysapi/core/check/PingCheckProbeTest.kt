package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.core.domain.system.Platform
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PingCheckProbeTest {

    private val ping = mockk<Ping>()

    private lateinit var probe: PingCheckProbe

    @BeforeEach
    fun setUp() {
        every { ping.platform } returns Platform.LINUX
        every { ping.supported } returns true
        every { ping.installed() } returns true
        every { ping.run("127.0.0.1", any()) } returns Result.success(LINUX_REPLY)
        probe = PingCheckProbe(ping)
    }

    @Test
    fun `takes the round trip time the ping command reports as the latency`() {
        // Given
        every { ping.run("router.lan", 20) } returns Result.success(LINUX_REPLY)

        // When
        val result = probe.probe(pingCheckSpec(host = "router.lan"))

        // Then
        result.successful shouldBe true
        result.checkType shouldBe CheckType.PING
        result.latencyMs shouldBe 5
    }

    @Test
    fun `reads a reply out of windows ping output`() {
        // Given
        every { ping.run("router.lan", 20) } returns Result.success(WINDOWS_REPLY)

        // When
        val result = probe.probe(pingCheckSpec(host = "router.lan"))

        // Then
        result.successful shouldBe true
        result.latencyMs shouldBe 1
    }

    @Test
    fun `fails with what the ping command said when nothing answers`() {
        // Given
        every { ping.run("router.lan", 20) } returns Result.success(LINUX_NO_REPLY)

        // When
        val result = probe.probe(pingCheckSpec(host = "router.lan"))

        // Then
        result.successful shouldBe false
        result.message shouldContain "100% packet loss"
    }

    @Test
    fun `fails with the reason when this system has no ping to run`() {
        // Given
        every { ping.installed() } returns false

        // When
        val result = probe.probe(pingCheckSpec(host = "router.lan"))

        // Then
        result.successful shouldBe false
        result.message shouldContain "was not found on this system"
        verify(exactly = 0) { ping.run("router.lan", any()) }
    }

    @Test
    fun `fails when ping is installed but cannot send an echo request`() {
        // Given
        every { ping.run("127.0.0.1", any()) } returns Result.success("ping: socket: Operation not permitted")

        // When
        val result = probe.probe(pingCheckSpec(host = "router.lan"))

        // Then
        result.successful shouldBe false
        result.message shouldContain "cannot send ICMP echo requests"
    }

    @Test
    fun `reports ping as available with no reason when the loopback answers`() {
        // When
        val availability = probe.availability()

        // Then
        availability.available shouldBe true
        availability.unavailableReason shouldBe null
    }

    @Test
    fun `reports why ping is unavailable when this platform has no ping command`() {
        // Given
        every { ping.supported } returns false

        // When
        val availability = probe.availability()

        // Then
        availability.available shouldBe false
        availability.unavailableReason!! shouldContain "no ping command for LINUX"
    }

    @Test
    fun `answers the availability question once and remembers it`() {
        // Given
        every { ping.installed() } returns false

        // When
        probe.availability()
        every { ping.installed() } returns true
        val availability = probe.availability()

        // Then
        availability.available shouldBe false
        verify(exactly = 1) { ping.installed() }
    }

    companion object {
        private val LINUX_REPLY = """
            PING router.lan (10.0.0.1) 56(84) bytes of data.
            64 bytes from 10.0.0.1: icmp_seq=1 ttl=64 time=5.02 ms

            --- router.lan ping statistics ---
            1 packets transmitted, 1 received, 0% packet loss, time 0ms
            rtt min/avg/max/mdev = 5.024/5.024/5.024/0.000 ms
        """.trimIndent()

        private val LINUX_NO_REPLY = """
            PING 10.0.0.99 (10.0.0.99) 56(84) bytes of data.

            --- 10.0.0.99 ping statistics ---
            1 packets transmitted, 0 received, 100% packet loss, time 0ms
        """.trimIndent()

        private val WINDOWS_REPLY = """
            Pinging router.lan [10.0.0.1] with 32 bytes of data:
            Reply from 10.0.0.1: bytes=32 time=1ms TTL=64

            Ping statistics for 10.0.0.1:
                Packets: Sent = 1, Received = 1, Lost = 0 (0% loss),
            Approximate round trip times in milli-seconds:
                Minimum = 1ms, Maximum = 1ms, Average = 1ms
        """.trimIndent()
    }
}
