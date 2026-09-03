package com.krillsson.sysapi.graphql

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ContainerResolverTest {

    @Test
    fun `sorts labels by key`() {
        // Given
        val labels = mapOf("z.label" to "1", "a.label" to "2")

        // When
        val result = labels.toDockerLabels()

        // Then
        result shouldBe listOf(DockerLabel("a.label", "2"), DockerLabel("z.label", "1"))
    }

    @Test
    fun `an empty label map produces an empty list`() {
        // Given
        val labels = emptyMap<String, String>()

        // When
        val result = labels.toDockerLabels()

        // Then
        result shouldBe emptyList()
    }
}
