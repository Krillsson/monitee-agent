package com.krillsson.sysapi.workload.icon

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DashboardIconIndexTest {

    private val index = DashboardIconIndex.parse(
        listOf("plex\tsvg,png,webp", "home-assistant\tsvg,png,webp", "tailscale\tpng,webp", "", "tailscale-dark\tsvg")
    )

    @Test
    fun `finds an icon by its exact name ignoring case`() {
        // When
        val name = index.find("Plex")

        // Then
        name shouldBe "plex"
    }

    @Test
    fun `finds an icon whose name differs only in separators`() {
        // When
        val name = index.find("homeassistant")

        // Then
        name shouldBe "home-assistant"
    }

    @Test
    fun `prefers the shortest name when several simplify alike`() {
        // Given
        val index = DashboardIconIndex.parse(listOf("tail-scale-dark\tsvg", "tail-scale\tsvg", "tailscale\tsvg"))

        // When
        val name = index.find("tail_scale")

        // Then
        name shouldBe "tailscale"
    }

    @Test
    fun `finds nothing for an unknown name`() {
        // When
        val name = index.find("not-an-icon")

        // Then
        name shouldBe null
    }

    @Test
    fun `lists the formats an icon comes in`() {
        // When
        val formats = index.formats("tailscale")

        // Then
        formats shouldBe setOf("png", "webp")
    }
}
