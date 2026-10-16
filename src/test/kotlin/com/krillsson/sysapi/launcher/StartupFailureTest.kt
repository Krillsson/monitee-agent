package com.krillsson.sysapi.launcher

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.BindException
import java.nio.charset.UnsupportedCharsetException

class MissingReflectionRegistrationError(message: String) : Error(message)
class UnsupportedFeatureError(message: String) : Error(message)

class StartupFailureTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("nativeLimitations")
    fun `exits with the native limitation code for a native-image failure in a native image`(
        description: String,
        failure: Throwable
    ) {
        // When
        val exitCode = StartupFailure.exitCode(failure, inNativeImage = true)

        // Then
        exitCode shouldBe 86
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nativeLimitations")
    fun `leaves a native-image failure alone on the JVM`(description: String, failure: Throwable) {
        // When
        val exitCode = StartupFailure.exitCode(failure, inNativeImage = false)

        // Then
        exitCode shouldBe null
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ordinaryFailures")
    fun `leaves an ordinary startup failure alone in a native image`(description: String, failure: Throwable) {
        // When
        val exitCode = StartupFailure.exitCode(failure, inNativeImage = true)

        // Then
        exitCode shouldBe null
    }

    @Test
    fun `finds a native limitation however deep it is wrapped`() {
        // Given
        val failure = IllegalStateException(
            "Application run failed",
            RuntimeException("Error creating bean", ExceptionInInitializerError(UnsupportedCharsetException("Windows-1252")))
        )

        // Then
        StartupFailure.isNativeLimitation(failure) shouldBe true
    }

    @Test
    fun `stops walking a cause chain that loops`() {
        // Given
        val outer = RuntimeException("outer")
        val inner = RuntimeException("inner", outer)
        outer.initCause(inner)

        // Then
        StartupFailure.isNativeLimitation(outer) shouldBe false
    }

    companion object {
        @JvmStatic
        fun nativeLimitations() = listOf(
            Arguments.of("missing reflection", MissingReflectionRegistrationError("Container")),
            Arguments.of("unsupported feature", UnsupportedFeatureError("defining classes at runtime")),
            Arguments.of("charset", ExceptionInInitializerError(UnsupportedCharsetException("Windows-1252"))),
            Arguments.of("class not found", RuntimeException(ClassNotFoundException("org.hibernate.community.dialect.SQLiteDialect"))),
            Arguments.of("jni method", NoSuchMethodError("initDotNet")),
            Arguments.of("jni library", UnsatisfiedLinkError("no jni4net")),
            Arguments.of(
                "jackson",
                IllegalArgumentException(
                    "Cannot construct instance of `Container`: cannot deserialize from Object value (no delegate- or property-based Creator): this appears to be a native image"
                )
            )
        )

        @JvmStatic
        fun ordinaryFailures() = listOf(
            Arguments.of("port in use", IllegalStateException("Web server failed to start", BindException("Address already in use"))),
            Arguments.of("bad configuration", IllegalArgumentException("Could not parse configuration.yml")),
            Arguments.of("database", RuntimeException("Validate failed: Migrations have failed validation"))
        )
    }
}
