package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.core.domain.system.Platform
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

class PingTest {

    @ParameterizedTest
    @CsvSource(
        "LINUX, ping -n -c 1 -W 20 router.lan",
        "MACOS, ping -n -c 1 -t 20 router.lan",
        "FREEBSD, ping -n -c 1 -t 20 router.lan",
        "WINDOWS, ping -n 1 -w 20000 router.lan"
    )
    fun `speaks the ping dialect of the platform it runs on`(platform: Platform, expected: String) {
        // Given
        val ping = Ping(platform)

        // When
        val command = ping.command("router.lan", 20)

        // Then
        command shouldBe expected
    }

    @ParameterizedTest
    @EnumSource(Platform::class, names = ["SOLARIS", "AIX", "UNKNOWN"])
    fun `has no command for a platform it does not know`(platform: Platform) {
        // Given
        val ping = Ping(platform)

        // When
        val command = ping.command("router.lan", 20)

        // Then
        ping.supported shouldBe false
        command shouldBe null
    }

    @ParameterizedTest
    @ValueSource(strings = ["router.lan; rm -rf \$HOME", "\$(whoami).lan", "router.lan && reboot", "router'lan"])
    fun `refuses to build a command out of a host that would reach the shell`(host: String) {
        // Given
        val ping = Ping(Platform.LINUX)

        // When
        val result = ping.run(host, 1)

        // Then
        result.isFailure shouldBe true
        result.exceptionOrNull()?.message shouldContain "is not a host name or address"
    }

    @Test
    fun `finds the round trip time wherever the ping dialect puts it`() {
        // Given
        val iputils = "64 bytes from 10.0.0.1: icmp_seq=1 ttl=64 time=5.02 ms"
        val windows = "Reply from 10.0.0.1: bytes=32 time=1ms TTL=64"
        val windowsSubMillisecond = "Reply from 10.0.0.1: bytes=32 time<1ms TTL=64"
        val localised = "Antwort von 10.0.0.1: Bytes=32 Zeit=3ms TTL=64"

        // When / Then
        Ping.roundTripMillis(iputils) shouldBe 5.02
        Ping.roundTripMillis(windows) shouldBe 1.0
        Ping.roundTripMillis(windowsSubMillisecond) shouldBe 1.0
        Ping.roundTripMillis(localised) shouldBe 3.0
    }

    @Test
    fun `finds no round trip time in output that carries no reply`() {
        // Given
        val noReply = """
            --- 10.0.0.99 ping statistics ---
            1 packets transmitted, 0 received, 100% packet loss, time 0ms
        """.trimIndent()
        val unreachable = "Reply from 10.0.0.5: Destination host unreachable."

        // When / Then
        Ping.roundTripMillis(noReply) shouldBe null
        Ping.roundTripMillis(unreachable) shouldBe null
    }
}
