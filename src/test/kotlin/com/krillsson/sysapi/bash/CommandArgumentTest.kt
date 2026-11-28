package com.krillsson.sysapi.bash

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CommandArgumentTest {

    @ParameterizedTest
    @ValueSource(strings = ["ssh.service", "getty@tty1.service", "systemd-fsck@dev-disk-by\\x2duuid-1234.service", "dbus-org.freedesktop.login1.service"])
    fun `accepts systemd unit names`(name: String) {
        // When / Then
        CommandArgument.isUnitName(name) shouldBe true
    }

    @ParameterizedTest
    @ValueSource(strings = ["a;b", "a b", "\$(b)", "`b`", "a|b", "a&&b", "a'", "a\"", "a>b", "--root=/", "-.mount", "", "a\nb"])
    fun `refuses unit names that carry shell syntax or start an option`(name: String) {
        // When / Then
        CommandArgument.isUnitName(name) shouldBe false
    }

    @ParameterizedTest
    @ValueSource(strings = ["ups", "APC_Back-UPS.1", "eaton5p"])
    fun `accepts UPS names`(name: String) {
        // When / Then
        CommandArgument.isUpsName(name) shouldBe true
    }

    @ParameterizedTest
    @ValueSource(strings = ["a;b", "ups@other", "-L", "ups name", "\$(b)", ""])
    fun `refuses UPS names that are not plain NUT names`(name: String) {
        // When / Then
        CommandArgument.isUpsName(name) shouldBe false
    }

    @ParameterizedTest
    @ValueSource(strings = ["/dev/sda", "/dev/nvme0n1", "disk0", "/dev/disk/by-id/ata-WDC_WD40EFRX-68N32N0_WD-1234"])
    fun `accepts device paths`(path: String) {
        // When / Then
        CommandArgument.isDevicePath(path) shouldBe true
    }

    @ParameterizedTest
    @ValueSource(strings = ["/dev/sda;b", "-d", "/dev/sd a", "\$(b)", ""])
    fun `refuses device paths that carry shell syntax or start an option`(path: String) {
        // When / Then
        CommandArgument.isDevicePath(path) shouldBe false
    }

    @ParameterizedTest
    @ValueSource(strings = ["router.lan", "10.0.0.1", "fe80::1%eth0", "[::1]", "localhost"])
    fun `accepts host names and addresses`(host: String) {
        // When / Then
        CommandArgument.isHost(host) shouldBe true
    }

    @ParameterizedTest
    @ValueSource(strings = ["-f", "router.lan;b", "router lan", "\$(b)", "router/lan", ""])
    fun `refuses hosts that carry shell syntax or start an option`(host: String) {
        // When / Then
        CommandArgument.isHost(host) shouldBe false
    }

    @Test
    fun `refuses an argument longer than any name it stands for`() {
        // Given
        val name = "a".repeat(256)

        // When / Then
        CommandArgument.isUnitName(name) shouldBe false
    }
}
