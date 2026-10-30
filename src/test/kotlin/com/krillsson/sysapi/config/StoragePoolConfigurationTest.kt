package com.krillsson.sysapi.config

import com.fasterxml.jackson.databind.node.ObjectNode
import com.krillsson.sysapi.configFileParser
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StoragePoolConfigurationTest {

    private val parser = configFileParser()

    private fun bundledConfig(): ObjectNode =
        parser.readTree(javaClass.getResourceAsStream("/config/configuration.yml")) as ObjectNode

    private fun ObjectNode.parse(): YAMLConfigFile = parser.treeToValue(this, YAMLConfigFile::class.java)

    @Test
    fun `is enabled in the bundled configuration`() {
        // Given / When
        val config = bundledConfig().parse()

        // Then
        config.storagePools.enabled shouldBe true
    }

    @Test
    fun `is enabled when the section is missing`() {
        // Given
        val yaml = bundledConfig().apply { remove("storagePools") }

        // When
        val config = yaml.parse()

        // Then
        config.storagePools.enabled shouldBe true
    }

    @Test
    fun `is enabled when the section leaves out enabled`() {
        // Given
        val yaml = bundledConfig().apply { putObject("storagePools") }

        // When
        val config = yaml.parse()

        // Then
        config.storagePools.enabled shouldBe true
    }
}
