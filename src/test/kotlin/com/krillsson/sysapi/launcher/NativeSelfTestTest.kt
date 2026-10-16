package com.krillsson.sysapi.launcher

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class NativeSelfTestTest {

    private val output = ByteArrayOutputStream()
    private val out = PrintStream(output, true)

    @Test
    fun `passes when every check passes`() {
        // Given
        val selfTest = NativeSelfTest(listOf(NativeSelfTest.Check("a") {}, NativeSelfTest.Check("b") {}))

        // When
        val exitCode = selfTest.run(out, inNativeImage = true)

        // Then
        exitCode shouldBe 0
    }

    @Test
    fun `reports a native limitation with the native limitation code in a native image`() {
        // Given
        val selfTest = NativeSelfTest(
            listOf(
                NativeSelfTest.Check("ok") {},
                NativeSelfTest.Check("docker models") { throw MissingReflectionRegistrationError("Container") }
            )
        )

        // When
        val exitCode = selfTest.run(out, inNativeImage = true)

        // Then
        exitCode shouldBe 86
        output.toString() shouldContain "FAILED  docker models"
    }

    @Test
    fun `reports a native limitation as an ordinary failure on the JVM`() {
        // Given
        val selfTest = NativeSelfTest(listOf(NativeSelfTest.Check("x") { throw MissingReflectionRegistrationError("x") }))

        // Then
        selfTest.run(out, inNativeImage = false) shouldBe 1
    }

    @Test
    fun `reports any other failure as an ordinary failure`() {
        // Given
        val selfTest = NativeSelfTest(listOf(NativeSelfTest.Check("ohm") { check(false) { "did not initialise" } }))

        // Then
        selfTest.run(out, inNativeImage = true) shouldBe 1
    }

    @Test
    fun `reads every field of the canned container list`() {
        assertDoesNotThrow { NativeSelfTest.readContainerList() }
    }
}
