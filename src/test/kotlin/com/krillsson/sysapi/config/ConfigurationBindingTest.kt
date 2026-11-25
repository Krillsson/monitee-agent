package com.krillsson.sysapi.config

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit

class ConfigurationBindingTest {

    private val minimal = """
        user:
          username: user
          password: password
        metricsConfig: {}
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

    private fun jackson(yaml: String): YAMLConfigFile =
        configFileParser().readValue(yaml, YAMLConfigFile::class.java)

    private fun withEmptyListsForNulls(config: YAMLConfigFile): YAMLConfigFile = config.copy(
        logReader = config.logReader.copy(
            files = config.logReader.files.orEmpty(),
            directories = config.logReader.directories.orEmpty()
        )
    )

    @Test
    fun `the shipped configuration binds to what Jackson produced`() {
        // Given
        val yaml = File("config/configuration.yml").readText()

        // When
        val bound = bind(yaml)

        // Then
        bound shouldBe withEmptyListsForNulls(jackson(yaml))
    }

    @Test
    fun `the bundled copy of the configuration binds to what Jackson produced`() {
        // Given
        val yaml = javaClass.getResourceAsStream("/config/configuration.yml")!!.readBytes().decodeToString()

        // When
        val bound = bind(yaml)

        // Then
        bound shouldBe withEmptyListsForNulls(jackson(yaml))
    }

    @Test
    fun `a blank list in the shipped configuration binds to an empty list`() {
        // Given
        val yaml = File("config/configuration.yml").readText()

        // When
        val bound = bind(yaml)

        // Then
        bound.logReader.directories shouldBe emptyList()
    }

    @Test
    fun `a minimal file binds with defaults for everything else`() {
        // Given / When
        val bound = bind(minimal)

        // Then
        bound shouldBe jackson(minimal)
        bound.proxmox.enabled shouldBe false
        bound.mDNS shouldBe MdnsConfiguration(false)
        bound.metricsConfig.history.unit shouldBe TimeUnit.MINUTES
    }

    @Test
    fun `the shipped configuration has no unknown keys`() {
        // Given
        val yaml = File("config/configuration.yml").readText()
        val names = BoundNames()

        // When
        bind(yaml, handler = names)

        // Then
        names.unknown(propertyNames(yaml)).shouldBeEmpty()
    }

    @Test
    fun `enum values bind regardless of case`() {
        // Given
        val yaml = minimal + "\nformatting:\n  temperatureUnit: FAHRENHEIT\nfileBrowser:\n  access: read_write\n"

        // When
        val bound = bind(yaml)

        // Then
        bound shouldBe jackson(yaml.replace("FAHRENHEIT", "fahrenheit").replace("read_write", "READ_WRITE"))
        bound.formatting.temperatureUnit shouldBe TemperatureUnit.fahrenheit
    }

    @Test
    fun `an environment variable overrides the file`() {
        // Given
        val yaml = minimal + "\nproxmox:\n  enabled: false\n  host: pve\n"

        // When
        val bound = bind(yaml, mapOf("PROXMOX_HOST" to "10.0.0.5", "PROXMOX_ENABLED" to "true"))

        // Then
        bound.proxmox.host shouldBe "10.0.0.5"
        bound.proxmox.enabled shouldBe true
    }

    @Test
    fun `an environment variable overrides a nested value and a camel cased key`() {
        // Given
        val environment = mapOf(
            "USER_USERNAME" to "admin",
            "METRICSCONFIG_CACHE_ENABLED" to "false",
            "FORWARDHTTPTOHTTPS" to "true",
            "MDNS_ENABLED" to "false"
        )

        // When
        val bound = bind(minimal, environment)

        // Then
        bound.user.username shouldBe "admin"
        bound.user.password shouldBe "password"
        bound.metricsConfig.cache.enabled shouldBe false
        bound.forwardHttpToHttps shouldBe true
        bound.mDNS.enabled shouldBe false
    }

    @Test
    fun `unrelated environment variables are ignored`() {
        // Given
        val environment = mapOf("USER" to "root", "HOME" to "/root", "PATH" to "/usr/bin")

        // When
        val bound = bind(minimal, environment)

        // Then
        bound shouldBe jackson(minimal)
    }

    @Test
    fun `lists of objects bind`() {
        // Given
        val yaml = minimal + "\ninternetServicesCheck:\n  services:\n    - address: a.com\n      name: A\n      port: 80\n"

        // When
        val bound = bind(yaml)

        // Then
        bound shouldBe jackson(yaml)
        bound.internetServicesCheck.services.map { it.name }.shouldContainExactly("A")
    }

    @Test
    fun `blank values of optional text keys bind to null like Jackson`() {
        // Given
        val yaml = minimal.replace("docker: {}\n", "") + """

            docker:
              host:
            mqtt:
              enabled: true
              password:
              username: ""
            proxmox:
              caCertificatePath:
            linux:
              overrideCpuTempSensor:
            notifications:
              serverName:
              ntfy:
                topic:
              webhooks:
                - url: "http://a"
                  body:
        """.trimIndent().let { "\n" + it }

        // When
        val bound = bind(yaml)

        // Then
        bound.docker.host shouldBe null
        bound.mqtt.password shouldBe null
        bound.proxmox.caCertificatePath shouldBe null
        bound.linux.overrideCpuTempSensor shouldBe null
        bound.notifications.serverName shouldBe null
        bound.notifications.ntfy.topic shouldBe null
        bound.notifications.webhooks.single().body shouldBe null
        bound.mqtt.username shouldBe null
        val jackson = jackson(yaml)
        bound.docker.host shouldBe jackson.docker.host
        bound.mqtt.password shouldBe jackson.mqtt.password
        bound.proxmox.caCertificatePath shouldBe jackson.proxmox.caCertificatePath
        bound.notifications.webhooks.single().body shouldBe jackson.notifications.webhooks.single().body
    }

    @Test
    fun `blank values of required text keys stay blank`() {
        // Given
        val yaml = minimal.replace("password: password", "password: \"\"") + "\nproxmox:\n  tokenId: \"\"\n  host: \"\"\n"

        // When
        val bound = bind(yaml)

        // Then
        bound.user.password shouldBe ""
        bound.proxmox.tokenId shouldBe ""
        bound.proxmox.host shouldBe ""
    }
}
