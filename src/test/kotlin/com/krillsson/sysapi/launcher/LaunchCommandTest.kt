package com.krillsson.sysapi.launcher

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class LaunchCommandTest {

    private val output = ByteArrayOutputStream()
    private val out = PrintStream(output, true)
    private val passingSelfTest = NativeSelfTest(listOf(NativeSelfTest.Check("nothing") {}))

    @Test
    fun `answers the probe with the version and the runtime`() {
        // When
        val exitCode = LaunchCommand.run(arrayOf("--native-probe"), out, inNativeImage = true) { passingSelfTest }

        // Then
        exitCode shouldBe 0
        output.toString().trim() shouldBe LaunchCommand.probeLine(com.krillsson.sysapi.BuildConfig.APP_VERSION, true)
    }

    @Test
    fun `names the runtime in the probe line`() {
        LaunchCommand.probeLine("1.2.3", inNativeImage = true) shouldBe "sysapi 1.2.3 native"
        LaunchCommand.probeLine("1.2.3", inNativeImage = false) shouldBe "sysapi 1.2.3 jvm"
    }

    @Test
    fun `runs the self-test`() {
        // When
        val exitCode = LaunchCommand.run(arrayOf("--native-self-test"), out, inNativeImage = true) { passingSelfTest }

        // Then
        exitCode shouldBe 0
        output.toString().trim() shouldBe "ok      nothing"
    }

    @Test
    fun `lets the application start for any other arguments`() {
        LaunchCommand.run(arrayOf("--server.port=8080"), out, inNativeImage = true) { passingSelfTest } shouldBe null
        LaunchCommand.run(emptyArray(), out, inNativeImage = true) { passingSelfTest } shouldBe null
    }
}
