package com.krillsson.sysapi.workload.icon

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ImageNamesTest {

    @ParameterizedTest
    @CsvSource(
        "plex, plex",
        "plex:latest, plex",
        "linuxserver/plex, plex|linuxserver-plex",
        "lscr.io/linuxserver/plex:1.41.0, plex|linuxserver-plex",
        "docker.io/library/nginx:alpine, nginx",
        "library/redis, redis",
        "ghcr.io/home-assistant/home-assistant:stable, home-assistant|home-assistant-home-assistant",
        "localhost:5000/team/app:1, app|team-app",
        "postgres@sha256:0123456789abcdef, postgres",
        "Jellyfin/Jellyfin:10.9@sha256:0123456789abcdef, jellyfin|jellyfin-jellyfin",
        "sha256:0123456789abcdef, ''",
        "'', ''"
    )
    fun `reduces an image reference to the names an icon can have`(image: String, expected: String) {
        // When
        val candidates = ImageNames.dockerCandidates(image)

        // Then
        candidates shouldBe expected.split('|').filter { it.isNotEmpty() }
    }
}
