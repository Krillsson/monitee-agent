package com.krillsson.sysapi.config

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test

class BoundNamesTest {

    private val base = """
        user:
          username: user
          password: password
        metricsConfig:
          history:
            interval: 30
        windows: {}
        connectivityCheck:
          enabled: true
          address: https://ifconfig.me
        docker: {}
        forwardHttpToHttps: false
        selfSignedCertificates:
          enabled: true
          populateCN: true
          populateSAN: true
    """.trimIndent()

    private fun unknownIn(yaml: String): List<String> {
        val names = BoundNames()
        bind(yaml, handler = names)
        return names.unknown(propertyNames(yaml))
    }

    @Test
    fun `reports a key that no property binds`() {
        // Given
        val yaml = base.replace("  history:\n    interval: 30", "  history:\n    interval: 30\n  monitor:\n    interval: 15")

        // When
        val unknown = unknownIn(yaml)

        // Then
        unknown.shouldContainExactly("metricsConfig.monitor.interval")
    }

    @Test
    fun `reports a misspelled key`() {
        // Given
        val yaml = base + "\nproxmox:\n  enabled: true\n  hots: pve\n"

        // When
        val unknown = unknownIn(yaml)

        // Then
        unknown.shouldContainExactly("proxmox.hots")
    }

    @Test
    fun `reports an unknown top level key`() {
        // Given
        val yaml = base + "\nmonitoring:\n  enabled: true\n"

        // When
        val unknown = unknownIn(yaml)

        // Then
        unknown.shouldContainExactly("monitoring.enabled")
    }

    @Test
    fun `does not report nested keys that bind`() {
        // Given
        val yaml = base + "\nproxmox:\n  enabled: true\n  host: pve\n"

        // When
        val unknown = unknownIn(yaml)

        // Then
        unknown.shouldBeEmpty()
    }

    @Test
    fun `does not report keys in lists of objects or scalar lists`() {
        // Given
        val yaml = base + """

            logReader:
              files:
                - /var/log/syslog
            internetServicesCheck:
              services:
                - address: a.com
                  name: A
                  port: 80
        """.trimIndent()

        // When
        val unknown = unknownIn(yaml)

        // Then
        unknown.shouldBeEmpty()
    }

    @Test
    fun `reports a misspelled key inside a list of objects`() {
        // Given
        val yaml = base + "\ninternetServicesCheck:\n  services:\n    - address: a.com\n      name: A\n      nam: B\n      port: 80\n"

        // When
        val unknown = unknownIn(yaml)

        // Then
        unknown.shouldContainExactly("internetServicesCheck.services[0].nam")
    }

    @Test
    fun `matches keys regardless of case and dashes`() {
        // Given
        val yaml = base + "\nmdns:\n  enabled: true\nproxmox:\n  poll-interval-seconds: 5\n"

        // When
        val unknown = unknownIn(yaml)

        // Then
        unknown.shouldBeEmpty()
    }
}
