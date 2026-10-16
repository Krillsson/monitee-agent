package com.krillsson.sysapi

import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinDef
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates

class RuntimeHintTest {

    @Test
    fun `registers the fields of Windows JNA structures when built on Windows`() {
        // Given
        val hints = RuntimeHints()

        // When
        RuntimeHint(aotOnWindows = true).registerHints(hints, javaClass.classLoader)

        // Then
        RuntimeHintsPredicates.reflection().onField(Guid.GUID::class.java.getField("Data1")).test(hints) shouldBe true
        RuntimeHintsPredicates.reflection().onConstructor(WinDef.DWORD::class.java.getConstructor()).test(hints) shouldBe true
        RuntimeHintsPredicates.proxies().forInterfaces(Kernel32::class.java).test(hints) shouldBe true
    }

    @Test
    fun `leaves Windows JNA out of a build on any other OS`() {
        // Given
        val hints = RuntimeHints()

        // When
        RuntimeHint(aotOnWindows = false).registerHints(hints, javaClass.classLoader)

        // Then
        RuntimeHintsPredicates.reflection().onType(Guid.GUID::class.java).test(hints) shouldBe false
    }
}
