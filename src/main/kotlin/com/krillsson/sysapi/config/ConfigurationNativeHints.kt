package com.krillsson.sysapi.config

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    CacheConfiguration::class,
    ConnectivityCheckConfiguration::class,
    ContainerUpdateCheckConfiguration::class,
    ContainerUpdateNotifyStyle::class,
    RegistryConfiguration::class,
    DockerConfiguration::class,
    HistoryConfiguration::class,
    HistoryPurgingConfiguration::class,
    CheckHistoryConfiguration::class,
    RetentionConfiguration::class,
    MetricSeriesConfiguration::class,
    MetricSamplingConfiguration::class,
    IntervalConfiguration::class,
    LinuxConfiguration::class,
    LogReaderConfiguration::class,
    MdnsConfiguration::class,
    MetricsConfiguration::class,
    UpsConfiguration::class,
    StoragePoolConfiguration::class,
    ProxmoxConfiguration::class,
    UnraidConfiguration::class,
    ProcessesConfiguration::class,
    SelfSignedCertificateConfiguration::class,
    UpdateCheckConfiguration::class,
    UpnpIgdConfiguration::class,
    UserConfiguration::class,
    WindowsConfiguration::class,
    YAMLConfigFile::class,
    NotificationsConfiguration::class,
    NotificationsConfiguration.NtfyConfiguration::class,
    NotificationsConfiguration.WebhookConfiguration::class,
    MqttConfiguration::class,
    MqttConfiguration.HomeAssistantConfiguration::class,
    FileBrowserConfiguration::class,
    FileBrowserAccess::class,
)
class ConfigurationNativeHints
