package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ComposeLabels
import com.krillsson.sysapi.core.domain.docker.ContainerGroup
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ContainerGroupingTest {

    @Test
    fun `containers sharing a compose project land in one group`() {
        // Given
        val web = container("web", "web", labels = mapOf(ComposeLabels.PROJECT to "myapp"))
        val db = container("db", "db", labels = mapOf(ComposeLabels.PROJECT to "myapp"))

        // When
        val groups = listOf(web, db).groupByComposeProject()

        // Then
        groups shouldBe listOf(ContainerGroup("myapp", listOf(web, db)))
    }

    @Test
    fun `containers with no label and a blank label share one ungrouped bucket`() {
        // Given
        val noLabel = container("a", "a")
        val blankLabel = container("b", "b", labels = mapOf(ComposeLabels.PROJECT to ""))

        // When
        val groups = listOf(noLabel, blankLabel).groupByComposeProject()

        // Then
        groups shouldBe listOf(ContainerGroup(null, listOf(noLabel, blankLabel)))
    }

    @Test
    fun `an all-compose set of containers produces no ungrouped group`() {
        // Given
        val web = container("web", "web", labels = mapOf(ComposeLabels.PROJECT to "myapp"))

        // When
        val groups = listOf(web).groupByComposeProject()

        // Then
        groups.none { it.composeProject == null } shouldBe true
    }

    @Test
    fun `groups are sorted case-insensitively by project, ungrouped last`() {
        // Given
        val zeta = container("z", "z", labels = mapOf(ComposeLabels.PROJECT to "Zeta"))
        val alpha = container("a", "a", labels = mapOf(ComposeLabels.PROJECT to "alpha"))
        val ungrouped = container("u", "u")

        // When
        val groups = listOf(zeta, alpha, ungrouped).groupByComposeProject()

        // Then
        groups.map { it.composeProject } shouldBe listOf("alpha", "Zeta", null)
    }
}
