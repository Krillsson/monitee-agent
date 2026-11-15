package com.krillsson.sysapi.workload.icon

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ContainerIconsTest {

    private val icons = ContainerIcons()
    private val cdn = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons"

    @Test
    fun `links every format of the icon matching the image name`() {
        // When
        val icon = icons.forDockerImage("lscr.io/linuxserver/plex:latest")

        // Then
        icon shouldBe ContainerIcon(
            name = "plex",
            isFallback = false,
            svg = "$cdn/svg/plex.svg",
            png = "$cdn/png/plex.png",
            webp = "$cdn/webp/plex.webp"
        )
    }

    @Test
    fun `falls back to the whole repository name when the last segment has no icon`() {
        // When
        val icon = icons.iconFor(listOf("zzz-unknown", "linuxserver-io"), "docker")

        // Then
        icon.name shouldBe "linuxserver-io"
        icon.isFallback shouldBe false
    }

    @Test
    fun `shows the docker icon when the image matches nothing`() {
        // When
        val icon = icons.forDockerImage("example.com/acme/zzz-not-an-icon:1")

        // Then
        icon shouldBe ContainerIcon(
            name = "docker",
            isFallback = true,
            svg = "$cdn/svg/docker.svg",
            png = "$cdn/png/docker.png",
            webp = "$cdn/webp/docker.webp"
        )
    }

    @Test
    fun `shows the docker icon for an image that is only an id`() {
        // When
        val icon = icons.forDockerImage("sha256:0123456789abcdef")

        // Then
        icon.name shouldBe "docker"
        icon.isFallback shouldBe true
    }

    @Test
    fun `matches an lxc container on its name before its os type`() {
        // When
        val icon = icons.forLxc("plex", "debian")

        // Then
        icon.name shouldBe "plex"
    }

    @Test
    fun `matches an lxc container on its os type when its name has no icon`() {
        // When
        val icon = icons.forLxc("zzz-box", "nginx")

        // Then
        icon.name shouldBe "nginx"
    }

    @Test
    fun `shows the proxmox icon for an lxc container that matches nothing`() {
        // When
        val icon = icons.forLxc("zzz-box", null)

        // Then
        icon.name shouldBe "proxmox"
        icon.isFallback shouldBe true
    }

    @Test
    fun `leaves out a format the icon does not come in`() {
        // When
        val icon = icons.iconFor(listOf("3xui"), "docker")

        // Then
        icon shouldBe ContainerIcon(
            name = "3xui",
            isFallback = false,
            svg = null,
            png = "$cdn/png/3xui.png",
            webp = "$cdn/webp/3xui.webp"
        )
    }
}
