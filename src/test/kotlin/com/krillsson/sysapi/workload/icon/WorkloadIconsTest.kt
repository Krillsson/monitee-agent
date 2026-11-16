package com.krillsson.sysapi.workload.icon

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WorkloadIconsTest {

    private val icons = WorkloadIcons()
    private val cdn = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons"

    @Test
    fun `links every format of the icon matching the image name`() {
        // When
        val icon = icons.forDockerImage("lscr.io/linuxserver/plex:latest")

        // Then
        icon shouldBe WorkloadIcon(
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
        val icon = icons.iconFor(sequenceOf("zzz-unknown", "linuxserver-io"), "docker")

        // Then
        icon.name shouldBe "linuxserver-io"
        icon.isFallback shouldBe false
    }

    @Test
    fun `shows the docker icon when the image matches nothing`() {
        // When
        val icon = icons.forDockerImage("example.com/qqq/zzz-qqq:1")

        // Then
        icon shouldBe WorkloadIcon(
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
        val icon = icons.forLxc("plex") { "debian" }

        // Then
        icon.name shouldBe "plex"
    }

    @Test
    fun `matches an lxc container on its os type when its name has no icon`() {
        // When
        val icon = icons.forLxc("zzz-qqq") { "debian" }

        // Then
        icon.name shouldBe "debian-linux"
    }

    @Test
    fun `shows the proxmox icon for an lxc container that matches nothing`() {
        // When
        val icon = icons.forLxc("zzz-qqq") { null }

        // Then
        icon.name shouldBe "proxmox"
        icon.isFallback shouldBe true
    }

    @Test
    fun `leaves out a format the icon does not come in`() {
        // When
        val icon = icons.iconFor(sequenceOf("3xui"), "docker")

        // Then
        icon shouldBe WorkloadIcon(
            name = "3xui",
            isFallback = false,
            svg = null,
            png = "$cdn/png/3xui.png",
            webp = "$cdn/webp/3xui.webp"
        )
    }

    @ParameterizedTest
    @CsvSource(
        "ghcr.io/binhex/arch-qbittorrent, qbittorrent",
        "copyparty/ac:latest, copyparty",
        "saspus/duplicacy-web, duplicacy",
        "eclipse-mosquitto:2, mosquitto",
        "plexinc/pms-docker, plex",
        "lscr.io/linuxserver/ddclient, ddclient"
    )
    fun `matches an image whose repository name only contains the project name`(image: String, expected: String) {
        // When
        val icon = icons.forDockerImage(image)

        // Then
        icon.name shouldBe expected
    }

    @Test
    fun `shows the docker icon rather than matching a generic word in the image name`() {
        // When
        val icon = icons.forDockerImage("zzz/zzz-docker")

        // Then
        icon.isFallback shouldBe true
    }

    @Test
    fun `matches an lxc container on a word of its name`() {
        // When
        val icon = icons.forLxc("plex-server") { null }

        // Then
        icon.name shouldBe "plex"
    }

    @Test
    fun `does not read the os type when the lxc container's name matches`() {
        // When
        val icon = icons.forLxc("plex") { error("os type read") }

        // Then
        icon.name shouldBe "plex"
    }

    @ParameterizedTest
    @CsvSource(
        "zzz-qqq, win11, windows-11",
        "zzz-qqq, wxp, windows-xp",
        "zzz-qqq, w2k8, microsoft-windows",
        "zzz-qqq, l26, linux",
        "opnsense, l26, opnsense",
        "zzz-qqq, other, proxmox",
        "zzz-qqq, , proxmox"
    )
    fun `matches a proxmox vm on its name before its ostype`(name: String, osType: String?, expected: String) {
        // When
        val icon = icons.forProxmoxVm(name) { osType }

        // Then
        icon.name shouldBe expected
    }

    @ParameterizedTest
    @CsvSource(
        "Windows 11, windows-11",
        "Windows 10 Gaming, windows-10",
        "Windows Server 2022, microsoft-windows",
        "Ubuntu, ubuntu-linux",
        "HAOS, home-assistant",
        "Home Assistant, home-assistant",
        "TrueNAS Scale, truenas-scale",
        "zzz qqq, unraid"
    )
    fun `matches an unraid vm on its name`(name: String, expected: String) {
        // When
        val icon = icons.forUnraidVm(name)

        // Then
        icon.name shouldBe expected
    }

    @Test
    fun `marks the unraid icon as the fallback for an unraid vm that matches nothing`() {
        // When
        val icon = icons.forUnraidVm("zzz qqq")

        // Then
        icon.isFallback shouldBe true
    }
}
