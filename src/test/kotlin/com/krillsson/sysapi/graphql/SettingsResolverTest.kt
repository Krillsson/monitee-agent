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
import com.krillsson.sysapi.config.IntervalConfiguration
import com.krillsson.sysapi.config.MetricSamplingConfiguration
import com.krillsson.sysapi.config.MetricSeriesConfiguration
import com.krillsson.sysapi.config.HistoryPurgingConfiguration
import com.krillsson.sysapi.config.InternetServicesCheckConfiguration
import com.krillsson.sysapi.config.LogReaderConfiguration
import com.krillsson.sysapi.config.NotificationsConfiguration
import com.krillsson.sysapi.config.RegistryConfiguration
import com.krillsson.sysapi.config.RetentionConfiguration
import com.krillsson.sysapi.config.SelfSignedCertificateConfiguration
import com.krillsson.sysapi.config.ServiceManagement
import com.krillsson.sysapi.config.SmartConfig
import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.MqttConfiguration
import com.krillsson.sysapi.config.UserConfiguration
import com.krillsson.sysapi.config.WindowsConfiguration
import com.krillsson.sysapi.config.WindowsEventLogConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import com.krillsson.sysapi.config.TemperatureUnit as ConfiguredTemperatureUnit
import com.krillsson.sysapi.notifications.localization.TemperatureUnit as ResolvedTemperatureUnit

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
    fun `normalises the metric series tiers and sampling cadences`() {
        // Given
        val history = HistoryConfiguration(
            series = MetricSeriesConfiguration(
                sampling = MetricSamplingConfiguration(
                    fast = IntervalConfiguration(1, TimeUnit.MINUTES),
                    slow = IntervalConfiguration(300, TimeUnit.SECONDS)
                ),
                raw = RetentionConfiguration(12, ChronoUnit.HOURS),
                fiveMinute = RetentionConfiguration(3, ChronoUnit.DAYS),
                hourly = RetentionConfiguration(90, ChronoUnit.DAYS),
                daily = RetentionConfiguration(2, ChronoUnit.YEARS)
            )
        )

        // When
        val result = history.toSettings()

        // Then
        result.metricFastSamplingSeconds shouldBe 60L
        result.metricSlowSamplingSeconds shouldBe 300L
        result.metricRawRetentionHours shouldBe 12L
        result.metricFiveMinuteRetentionHours shouldBe 72L
        result.metricHourlyRetentionDays shouldBe 90L
        result.metricDailyRetentionDays shouldBe 730L
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

    @ParameterizedTest
    @EnumSource(ResolvedTemperatureUnit::class)
    fun `maps every resolved temperature unit to a settings active unit`(unit: ResolvedTemperatureUnit) {
        // When
        val result = unit.toSettings()

        // Then
        result.name shouldBe unit.name.uppercase()
    }

    @Test
    fun `windowsManagement mirrors serviceManagement enabled, not the unrelated eventLog toggle`() {
        // Given
        val windows = WindowsConfiguration(
            serviceManagement = ServiceManagement(enabled = true),
            eventLog = WindowsEventLogConfiguration(enabled = false)
        )

        // When
        val result = windows.toSettings()

        // Then
        result.serviceManagementEnabled shouldBe true
    }

    @Test
    fun `notifications reports how many webhooks are configured, not their names`() {
        // Given
        val notifications = NotificationsConfiguration(
            ntfy = NotificationsConfiguration.NtfyConfiguration(enabled = true),
            webhooks = listOf(
                NotificationsConfiguration.WebhookConfiguration(name = "Discord"),
                NotificationsConfiguration.WebhookConfiguration(name = "Gotify")
            )
        )

        // When
        val result = notifications.toSettings()

        // Then
        result.ntfyEnabled shouldBe true
        result.webhooksConfigured shouldBe 2
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
        val serialized = ObjectMapper().writeValueAsString(config.toSettings(ActiveTemperatureUnit.CELSIUS))

        // Then
        secrets.forEach { secret -> serialized shouldNotContain secret }
    }
}
