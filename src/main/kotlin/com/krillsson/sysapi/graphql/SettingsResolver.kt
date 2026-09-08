package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.config.CacheConfiguration
import com.krillsson.sysapi.config.ContainerUpdateCheckConfiguration
import com.krillsson.sysapi.config.ContainerUpdateNotifyStyle
import com.krillsson.sysapi.config.DockerConfiguration
import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.config.FormattingConfiguration
import com.krillsson.sysapi.config.HistoryConfiguration
import com.krillsson.sysapi.config.LinuxConfiguration
import com.krillsson.sysapi.config.MqttConfiguration
import com.krillsson.sysapi.config.NotificationsConfiguration
import com.krillsson.sysapi.config.ProcessesConfiguration
import com.krillsson.sysapi.config.UpsConfiguration
import com.krillsson.sysapi.config.WindowsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.Duration
import java.time.temporal.ChronoUnit
import com.krillsson.sysapi.config.TemperatureUnit as ConfiguredTemperatureUnit

@Controller
class SettingsResolver(private val configFile: YAMLConfigFile) {

    @QueryMapping
    fun settings(): Settings = configFile.toSettings()
}

data class Settings(
    val processes: ProcessesSettings,
    val formatting: FormattingSettings,
    val history: HistorySettings,
    val cache: CacheSettings,
    val containerUpdateCheck: ContainerUpdateCheckSettings,
    val connectivity: ConnectivitySettings,
    val discovery: DiscoverySettings,
    val platform: PlatformSettings,
    val docker: DockerSettings,
    val ups: UpsSettings,
    val systemDaemon: SystemDaemonSettings,
    val windowsManagement: WindowsManagementSettings,
    val fileBrowser: FileBrowserSettings,
    val notifications: NotificationsSettings,
    val mqtt: MqttSettings
)

data class ProcessesSettings(val enabled: Boolean)

data class FormattingSettings(val temperatureUnit: TemperatureUnit)

enum class TemperatureUnit { SYSTEM, CELSIUS, FAHRENHEIT }

data class HistorySettings(
    val intervalSeconds: Long,
    val retentionDays: Long,
    val checkRawRetentionHours: Long,
    val checkHourlyRetentionDays: Long,
    val checkDailyRetentionDays: Long
)

data class CacheSettings(val enabled: Boolean, val durationSeconds: Long)

data class ContainerUpdateCheckSettings(
    val enabled: Boolean,
    val notify: Boolean,
    val notifyStyle: ContainerUpdateNotifyStyle,
    val digestAtHour: Int,
    val intervalMinutes: Long,
    val excludeContainers: List<String>
)

data class ConnectivitySettings(
    val connectivityCheckEnabled: Boolean,
    val internetServicesCheckEnabled: Boolean
)

data class DiscoverySettings(val mdnsEnabled: Boolean, val upnpEnabled: Boolean)

data class PlatformSettings(val cpuTempSensorOverride: String?)

data class DockerSettings(val enabled: Boolean)

data class UpsSettings(val enabled: Boolean)

data class SystemDaemonSettings(val enabled: Boolean)

data class WindowsManagementSettings(val serviceManagementEnabled: Boolean)

data class FileBrowserSettings(val enabled: Boolean)

data class NotificationsSettings(val ntfyEnabled: Boolean, val webhooksConfigured: Int)

data class MqttSettings(val enabled: Boolean)

fun YAMLConfigFile.toSettings(): Settings = Settings(
    processes = processes.toSettings(),
    formatting = formatting.toSettings(),
    history = metricsConfig.history.toSettings(),
    cache = metricsConfig.cache.toSettings(),
    containerUpdateCheck = docker.updateCheck.toSettings(),
    connectivity = ConnectivitySettings(
        connectivityCheckEnabled = connectivityCheck.enabled,
        internetServicesCheckEnabled = internetServicesCheck.enabled
    ),
    discovery = DiscoverySettings(mdnsEnabled = mDNS.enabled, upnpEnabled = upnp.enabled),
    platform = linux.toSettings(),
    docker = docker.toSettings(),
    ups = ups.toSettings(),
    systemDaemon = SystemDaemonSettings(enabled = linux.systemDaemonServiceManagement.enabled),
    windowsManagement = windows.toSettings(),
    fileBrowser = fileBrowser.toSettings(),
    notifications = notifications.toSettings(),
    mqtt = mqtt.toSettings()
)

fun ProcessesConfiguration.toSettings() = ProcessesSettings(enabled = enabled)

fun FormattingConfiguration.toSettings() = FormattingSettings(temperatureUnit = temperatureUnit.toSettings())

fun ConfiguredTemperatureUnit.toSettings(): TemperatureUnit = when (this) {
    ConfiguredTemperatureUnit.system -> TemperatureUnit.SYSTEM
    ConfiguredTemperatureUnit.celsius -> TemperatureUnit.CELSIUS
    ConfiguredTemperatureUnit.fahrenheit -> TemperatureUnit.FAHRENHEIT
}

fun HistoryConfiguration.toSettings() = HistorySettings(
    intervalSeconds = unit.toSeconds(interval),
    retentionDays = purging.unit.durationOf(purging.olderThan).toDays(),
    checkRawRetentionHours = checks.raw.unit.durationOf(checks.raw.olderThan).toHours(),
    checkHourlyRetentionDays = checks.hourly.unit.durationOf(checks.hourly.olderThan).toDays(),
    checkDailyRetentionDays = checks.daily.unit.durationOf(checks.daily.olderThan).toDays()
)

private fun ChronoUnit.durationOf(amount: Long): Duration = duration.multipliedBy(amount)

fun CacheConfiguration.toSettings() = CacheSettings(enabled = enabled, durationSeconds = unit.toSeconds(duration))

fun ContainerUpdateCheckConfiguration.toSettings() = ContainerUpdateCheckSettings(
    enabled = enabled,
    notify = notify,
    notifyStyle = notifyStyle,
    digestAtHour = digestAtHour,
    intervalMinutes = intervalMinutes,
    excludeContainers = excludeContainers
)

fun LinuxConfiguration.toSettings() = PlatformSettings(cpuTempSensorOverride = overrideCpuTempSensor)

fun DockerConfiguration.toSettings() = DockerSettings(enabled = enabled)

fun UpsConfiguration.toSettings() = UpsSettings(enabled = enabled)

fun WindowsConfiguration.toSettings() = WindowsManagementSettings(serviceManagementEnabled = serviceManagement.enabled)

fun FileBrowserConfiguration.toSettings() = FileBrowserSettings(enabled = enabled)

fun NotificationsConfiguration.toSettings() = NotificationsSettings(
    ntfyEnabled = ntfy.enabled,
    webhooksConfigured = webhooks.size
)

fun MqttConfiguration.toSettings() = MqttSettings(enabled = enabled)
