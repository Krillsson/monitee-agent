package com.krillsson.sysapi.graphql

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.AdditionalDeviceFlags
import com.krillsson.sysapi.config.CacheConfiguration
import com.krillsson.sysapi.config.CheckHistoryConfiguration
import com.krillsson.sysapi.config.ConnectivityCheckConfiguration
import com.krillsson.sysapi.config.ContainerUpdateCheckConfiguration
import com.krillsson.sysapi.config.DockerConfiguration
import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.config.HistoryConfiguration
import com.krillsson.sysapi.config.HistoryPurgingConfiguration
import com.krillsson.sysapi.config.InternetServicesCheckConfiguration
import com.krillsson.sysapi.config.LinuxConfiguration
import com.krillsson.sysapi.config.LogReaderConfiguration
import com.krillsson.sysapi.config.NotificationsConfiguration
import com.krillsson.sysapi.config.RegistryConfiguration
import com.krillsson.sysapi.config.RetentionConfiguration
import com.krillsson.sysapi.config.SelfSignedCertificateConfiguration
import com.krillsson.sysapi.config.SmartConfig
import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.MqttConfiguration
import com.krillsson.sysapi.config.UserConfiguration
import com.krillsson.sysapi.config.WindowsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import com.krillsson.sysapi.config.TemperatureUnit as ConfiguredTemperatureUnit

class SettingsResolverTest {

    @Test
    fun `normalises a history interval in minutes to seconds`() {
        // Given
        val history = HistoryConfiguration(interval = 30, unit = TimeUnit.MINUTES)

        // When
        val result = history.toSettings()

        // Then
        result.intervalSeconds shouldBe 1800L
    }

    @Test
    fun `normalises retention configured in hours to the same days value as one configured in days`() {
        // Given
        val configuredInDays = HistoryConfiguration(purging = HistoryPurgingConfiguration(14, ChronoUnit.DAYS, 1, TimeUnit.DAYS))
        val configuredInHours = HistoryConfiguration(purging = HistoryPurgingConfiguration(336, ChronoUnit.HOURS, 1, TimeUnit.DAYS))

        // When
        val fromDays = configuredInDays.toSettings()
        val fromHours = configuredInHours.toSettings()

        // Then
        fromDays.retentionDays shouldBe 14L
        fromHours.retentionDays shouldBe fromDays.retentionDays
    }

    @Test
    fun `normalises check retention tiers`() {
        // Given
        val history = HistoryConfiguration(
            checks = CheckHistoryConfiguration(
                raw = RetentionConfiguration(2, ChronoUnit.DAYS),
                hourly = RetentionConfiguration(90, ChronoUnit.DAYS),
                daily = RetentionConfiguration(2, ChronoUnit.YEARS)
            )
        )

        // When
        val result = history.toSettings()

        // Then
        result.checkRawRetentionHours shouldBe 48L
        result.checkHourlyRetentionDays shouldBe 90L
        result.checkDailyRetentionDays shouldBe 730L
    }

    @ParameterizedTest
    @EnumSource(ConfiguredTemperatureUnit::class)
    fun `maps every configured temperature unit to a settings temperature unit`(unit: ConfiguredTemperatureUnit) {
        // When
        val result = unit.toSettings()

        // Then
        result.name shouldBe unit.name.uppercase()
    }

    @Test
    fun `cpuTempSensorOverride is null when none is configured`() {
        // Given
        val linux = LinuxConfiguration(overrideCpuTempSensor = null)

        // When
        val result = linux.toSettings()

        // Then
        result.cpuTempSensorOverride shouldBe null
    }

    @Test
    fun `cpuTempSensorOverride carries the configured sensor identifier`() {
        // Given
        val linux = LinuxConfiguration(overrideCpuTempSensor = "hwmon:coretemp")

        // When
        val result = linux.toSettings()

        // Then
        result.cpuTempSensorOverride shouldBe "hwmon:coretemp"
    }

    @Test
    fun `no sensitive value from the never-exposed table survives the mapping`() {
        // Given
        val secrets = listOf(
            "s3cr3t-username",
            "s3cr3t-password",
            "s3cr3t-ntfy-token",
            "s3cr3t-webhook-url",
            "s3cr3t-webhook-header-value",
            "s3cr3t-webhook-username",
            "s3cr3t-webhook-password",
            "s3cr3t-mqtt-username",
            "s3cr3t-mqtt-password",
            "s3cr3t-registry-username",
            "s3cr3t-registry-password",
            "s3cr3t-common-name",
            "s3cr3t-subject-alternative-name",
            "s3cr3t-filebrowser-root",
            "s3cr3t-log-file",
            "s3cr3t-log-directory",
            "s3cr3t-docker-host",
            "s3cr3t-smart-device"
        )
        val config = YAMLConfigFile(
            user = UserConfiguration(username = "s3cr3t-username", password = "s3cr3t-password"),
            metricsConfig = MetricsConfiguration(),
            windows = WindowsConfiguration(),
            connectivityCheck = ConnectivityCheckConfiguration(enabled = true, address = "https://ifconfig.me"),
            internetServicesCheck = InternetServicesCheckConfiguration(),
            notifications = NotificationsConfiguration(
                ntfy = NotificationsConfiguration.NtfyConfiguration(
                    token = "s3cr3t-ntfy-token",
                    username = "s3cr3t-webhook-username",
                    password = "s3cr3t-webhook-password"
                ),
                webhooks = listOf(
                    NotificationsConfiguration.WebhookConfiguration(
                        url = "s3cr3t-webhook-url",
                        headers = mapOf("Authorization" to "s3cr3t-webhook-header-value"),
                        username = "s3cr3t-webhook-username",
                        password = "s3cr3t-webhook-password"
                    )
                )
            ),
            mqtt = MqttConfiguration(username = "s3cr3t-mqtt-username", password = "s3cr3t-mqtt-password"),
            docker = DockerConfiguration(
                host = "s3cr3t-docker-host",
                updateCheck = ContainerUpdateCheckConfiguration(
                    registries = listOf(
                        RegistryConfiguration(
                            host = "ghcr.io",
                            username = "s3cr3t-registry-username",
                            password = "s3cr3t-registry-password"
                        )
                    )
                )
            ),
            smart = SmartConfig(additionalDeviceFlags = listOf(AdditionalDeviceFlags(device = "s3cr3t-smart-device", flags = "-d sat"))),
            forwardHttpToHttps = false,
            logReader = LogReaderConfiguration(files = listOf("s3cr3t-log-file"), directories = listOf("s3cr3t-log-directory")),
            fileBrowser = FileBrowserConfiguration(roots = listOf("s3cr3t-filebrowser-root")),
            selfSignedCertificates = SelfSignedCertificateConfiguration(
                enabled = true,
                populateCN = true,
                populateSAN = true,
                commonName = "s3cr3t-common-name",
                subjectAlternativeNames = listOf("s3cr3t-subject-alternative-name")
            )
        )

        // When
        val serialized = ObjectMapper().writeValueAsString(config.toSettings())

        // Then
        secrets.forEach { secret -> serialized shouldNotContain secret }
    }
}
