package com.krillsson.sysapi.launcher

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LauncherHandshakeTest {

    @TempDir
    lateinit var directory: File

    @Test
    fun `writes the runtime to the ready file`() {
        // Given
        val readyFile = File(directory, "data/.native-ready")
        val handshake = LauncherHandshake(mapOf("SYSAPI_READY_FILE" to readyFile.path), inNativeImage = true, version = "1.2.3")

        // When
        handshake.onReady()

        // Then
        readyFile.readText() shouldBe "native"
    }

    @Test
    fun `records that native is disabled when the launcher asked the JVM to verify`() {
        // Given
        val marker = File(directory, "native-disabled")
        val handshake = LauncherHandshake(mapOf("SYSAPI_NATIVE_DISABLED_FILE" to marker.path), inNativeImage = false, version = "1.2.3")

        // When
        handshake.onReady()

        // Then
        marker.readLines().take(2) shouldBe listOf("1.2.3", "unclassified")
    }

    @Test
    fun `writes nothing when not started by the launcher`() {
        // Given
        val handshake = LauncherHandshake(emptyMap(), inNativeImage = true, version = "1.2.3")

        // When
        handshake.onReady()

        // Then
        directory.listFiles()!!.size shouldBe 0
    }

    @Test
    fun `watches the launcher only when it is another process`() {
        LauncherHandshake(mapOf("SYSAPI_LAUNCHER_PID" to "42"), true, "1").launcherPid(ownPid = 7) shouldBe 42L
        LauncherHandshake(mapOf("SYSAPI_LAUNCHER_PID" to "7"), true, "1").launcherPid(ownPid = 7) shouldBe null
        LauncherHandshake(mapOf("SYSAPI_LAUNCHER_PID" to "x"), true, "1").launcherPid(ownPid = 7) shouldBe null
        LauncherHandshake(emptyMap(), true, "1").launcherPid(ownPid = 7) shouldBe null
    }
}
