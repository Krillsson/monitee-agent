package com.krillsson.sysapi

import com.krillsson.sysapi.notifications.ntfy.NtfyApi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.aot.hint.BindingReflectionHintsRegistrar
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.core.annotation.AnnotatedElementUtils

class SysAPIApplicationTest {

    @ParameterizedTest
    @CsvSource(
        "getTopic",
        "getMessage",
        "getTitle",
        "getClickUrl",
        "getActions"
    )
    fun `registers the ntfy request body for Jackson on native images`(getter: String) {
        // Given
        val hints = boundHints()

        // When
        val registered = RuntimeHintsPredicates.reflection()
            .onMethod(NtfyApi.Notification::class.java.getMethod(getter))
            .test(hints)

        // Then
        registered shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "getAction",
        "getLabel",
        "getUrl"
    )
    fun `registers the ntfy action buttons for Jackson on native images`(getter: String) {
        // Given
        val hints = boundHints()

        // When
        val registered = RuntimeHintsPredicates.reflection()
            .onMethod(NtfyApi.Notification.Action::class.java.getMethod(getter))
            .test(hints)

        // Then
        registered shouldBe true
    }

    private fun boundHints(): RuntimeHints {
        val hints = RuntimeHints()
        val classes = AnnotatedElementUtils.findMergedAnnotation(
            SysAPIApplication::class.java,
            RegisterReflectionForBinding::class.java
        )!!.classes.map { it.java }
        BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), *classes.toTypedArray())
        return hints
    }
}
