package com.krillsson.sysapi.workload.icon

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ImageNamesTest {

    @ParameterizedTest
    @CsvSource(
        "plex, plex",
        "plex:latest, plex",
        "linuxserver/plex, plex|linuxserver-plex|linuxserver",
        "lscr.io/linuxserver/plex:1.41.0, plex|linuxserver-plex|linuxserver",
        "docker.io/library/nginx:alpine, nginx",
        "library/redis, redis",
        "ghcr.io/home-assistant/home-assistant:stable, home-assistant|home-assistant-home-assistant|assistant|home",
        "localhost:5000/team/app:1, app|team-app|team",
        "postgres@sha256:0123456789abcdef, postgres",
        "Jellyfin/Jellyfin:10.9@sha256:0123456789abcdef, jellyfin|jellyfin-jellyfin",
        "saspus/duplicacy-web, duplicacy-web|saspus-duplicacy-web|duplicacy|saspus",
        "plexinc/pms-docker, pms-docker|plexinc-pms-docker|plex|plexinc",
        "acme/my-big-app, my-big-app|acme-my-big-app|big-app|my-big|big|acme",
        "sha256:0123456789abcdef, ''",
        "'', ''"
    )
    fun `reduces an image reference to the names an icon can have`(image: String, expected: String) {
        // When
        val candidates = ImageNames.dockerCandidates(image)

        // Then
        candidates shouldBe expected.split('|').filter { it.isNotEmpty() }
    }

    @ParameterizedTest
    @CsvSource(
        "Plex, plex",
        "pms, plex",
        "plex-server-01, plex-server-01|server-01|plex-server|plex",
        "'', ''"
    )
    fun `reduces a container name to the names an icon can have`(name: String, expected: String) {
        // When
        val candidates = ImageNames.nameCandidates(name)

        // Then
        candidates shouldBe expected.split('|').filter { it.isNotEmpty() }
    }

    @ParameterizedTest
    @CsvSource(
        "debian, debian|debian-linux",
        "archlinux, archlinux|archlinux-linux"
    )
    fun `adds the linux suffix dashboard icons uses for distributions`(osType: String, expected: String) {
        // When
        val candidates = ImageNames.osTypeCandidates(osType)

        // Then
        candidates shouldBe expected.split('|')
    }
}
