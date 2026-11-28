package com.krillsson.sysapi.config

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ConfigurationFileLocationTest {

    @Test
    fun `imports the file in the config directory when it exists`(@TempDir dir: File) {
        // Given
        val file = File(dir, "configuration.yml").apply { writeText("a: b") }

        // When
        val import = ConfigurationFileLocation.import(file)

        // Then
        import shouldBe "optional:file:./config/configuration.yml"
    }

    @Test
    fun `falls back to the bundled copy when the file is missing`(@TempDir dir: File) {
        // Given
        val file = File(dir, "configuration.yml")

        // When
        val properties = ConfigurationFileLocation.defaultProperties(file)

        // Then
        properties shouldBe mapOf("spring.config.import" to "optional:classpath:/config/configuration.yml")
    }
}
