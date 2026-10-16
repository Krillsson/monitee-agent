package com.krillsson.sysapi.launcher

import java.util.Collections
import java.util.IdentityHashMap

object StartupFailure {
    const val NATIVE_LIMITATION_EXIT_CODE = 86

    private val nativeLimitationTypes = setOf(
        "java.lang.ClassNotFoundException",
        "java.lang.NoClassDefFoundError",
        "java.lang.NoSuchMethodException",
        "java.lang.NoSuchMethodError",
        "java.lang.NoSuchFieldException",
        "java.lang.NoSuchFieldError",
        "java.lang.UnsatisfiedLinkError",
        "java.nio.charset.UnsupportedCharsetException"
    )

    private val nativeImageErrorNames = setOf(
        "MissingReflectionRegistrationError",
        "MissingJNIRegistrationError",
        "MissingResourceRegistrationError",
        "MissingForeignRegistrationError",
        "UnsupportedFeatureError"
    )

    private const val JACKSON_NATIVE_IMAGE_HINT = "this appears to be a native image"

    fun exitCode(failure: Throwable, inNativeImage: Boolean): Int? =
        NATIVE_LIMITATION_EXIT_CODE.takeIf { inNativeImage && isNativeLimitation(failure) }

    fun isNativeLimitation(failure: Throwable): Boolean = causeChain(failure).any { cause ->
        cause.javaClass.name in nativeLimitationTypes ||
            cause.javaClass.simpleName in nativeImageErrorNames ||
            cause.message?.contains(JACKSON_NATIVE_IMAGE_HINT, ignoreCase = true) == true
    }

    private fun causeChain(failure: Throwable): Sequence<Throwable> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        return generateSequence(failure) { it.cause }.takeWhile { seen.add(it) }
    }
}
