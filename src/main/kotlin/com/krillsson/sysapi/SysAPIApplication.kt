package com.krillsson.sysapi

import SlowResolverWarningInstrumentation
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.core.DockerConfigFile
import com.krillsson.sysapi.config.*
import com.krillsson.sysapi.core.check.CheckHistory
import com.krillsson.sysapi.core.check.CheckHistoryPoint
import com.krillsson.sysapi.core.check.CheckResolution
import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.CheckType
import com.krillsson.sysapi.core.check.CheckUptime
import com.krillsson.sysapi.core.check.DnsCheck
import com.krillsson.sysapi.core.check.DnsRecordType
import com.krillsson.sysapi.core.check.HttpCheck
import com.krillsson.sysapi.core.check.HttpHeader
import com.krillsson.sysapi.core.check.HttpMethod
import com.krillsson.sysapi.core.check.PingAvailability
import com.krillsson.sysapi.core.check.PingCheck
import com.krillsson.sysapi.core.check.TcpCheck
import com.krillsson.sysapi.core.check.UptimeDay
import com.krillsson.sysapi.core.check.UptimeMetrics
import com.krillsson.sysapi.core.check.UptimePeriod
import com.krillsson.sysapi.core.domain.docker.ContainerImageUpdate
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateEligibility
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.ImagePullLayer
import com.krillsson.sysapi.core.domain.docker.ImagePullLayerPhase
import com.krillsson.sysapi.core.domain.docker.ImageUpdateStatus
import com.krillsson.sysapi.core.genericevents.ContainerImageUpdateAvailable
import com.krillsson.sysapi.core.genericevents.GenericEventStore
import com.krillsson.sysapi.filebrowser.ArchiveEntry
import com.krillsson.sysapi.filebrowser.ArchiveListing
import com.krillsson.sysapi.filebrowser.DirectoryListing
import com.krillsson.sysapi.filebrowser.DirectoryListingConnection
import com.krillsson.sysapi.filebrowser.DirectorySize
import com.krillsson.sysapi.filebrowser.FileBrowserError
import com.krillsson.sysapi.filebrowser.FileBrowserLimits
import com.krillsson.sysapi.filebrowser.FileEntry
import com.krillsson.sysapi.filebrowser.FileEntryEdge
import com.krillsson.sysapi.filebrowser.FileEntryType
import com.krillsson.sysapi.filebrowser.FileOperation
import com.krillsson.sysapi.filebrowser.FileOperationFailure
import com.krillsson.sysapi.filebrowser.FileOperationState
import com.krillsson.sysapi.filebrowser.FileOperationType
import com.krillsson.sysapi.filebrowser.FileSearchInput
import com.krillsson.sysapi.filebrowser.FileSearchResult
import com.krillsson.sysapi.filebrowser.TextFileContent
import com.krillsson.sysapi.filebrowser.TrashEntry
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerSkipped
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateJob
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateJobState
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateFailed
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateImagePullProgress
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateJob
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateJobState
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateStepChanged
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateSucceeded
import com.krillsson.sysapi.graphql.mutations.CopyFileInput
import com.krillsson.sysapi.graphql.mutations.CopyFilesInput
import com.krillsson.sysapi.graphql.mutations.CreateArchiveInput
import com.krillsson.sysapi.graphql.mutations.CreateCheckFailed
import com.krillsson.sysapi.graphql.mutations.CreateCheckSuccess
import com.krillsson.sysapi.graphql.mutations.CancelFileOperationInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryOutput
import com.krillsson.sysapi.graphql.mutations.CreateHttpCheckInput
import com.krillsson.sysapi.graphql.mutations.CreateDnsCheckInput
import com.krillsson.sysapi.graphql.mutations.CreatePingCheckInput
import com.krillsson.sysapi.graphql.mutations.CreateTcpCheckInput
import com.krillsson.sysapi.graphql.mutations.DeleteCheckInput
import com.krillsson.sysapi.graphql.mutations.DeleteCheckOutput
import com.krillsson.sysapi.graphql.mutations.DeleteFileInput
import com.krillsson.sysapi.graphql.mutations.DeleteFilesInput
import com.krillsson.sysapi.graphql.mutations.EmptyTrashInput
import com.krillsson.sysapi.graphql.mutations.ExtractArchiveInput
import com.krillsson.sysapi.graphql.mutations.HttpHeaderInput
import com.krillsson.sysapi.graphql.mutations.MoveFileInput
import com.krillsson.sysapi.graphql.mutations.FileOperationOutput
import com.krillsson.sysapi.graphql.mutations.MoveFilesInput
import com.krillsson.sysapi.graphql.mutations.MoveToTrashInput
import com.krillsson.sysapi.graphql.mutations.MoveToTrashOutput
import com.krillsson.sysapi.graphql.mutations.RestoreFromTrashInput
import com.krillsson.sysapi.graphql.mutations.RestoreFromTrashOutput
import com.krillsson.sysapi.graphql.mutations.OneOffHttpCheckInput
import com.krillsson.sysapi.graphql.mutations.OneOffDnsCheckInput
import com.krillsson.sysapi.graphql.mutations.OneOffPingCheckInput
import com.krillsson.sysapi.graphql.mutations.OneOffTcpCheckInput
import com.krillsson.sysapi.graphql.mutations.RunCheckNowFailed
import com.krillsson.sysapi.graphql.mutations.RunCheckNowInput
import com.krillsson.sysapi.graphql.mutations.RunCheckNowSuccess
import com.krillsson.sysapi.graphql.mutations.SaveTextFileInput
import com.krillsson.sysapi.graphql.mutations.SaveTextFileOutput
import com.krillsson.sysapi.graphql.mutations.SetCheckEnabledInput
import com.krillsson.sysapi.graphql.mutations.UpdateCheckFailed
import com.krillsson.sysapi.graphql.mutations.UpdateCheckSuccess
import com.krillsson.sysapi.graphql.mutations.AbortDockerContainerBatchUpdateInput
import com.krillsson.sysapi.graphql.mutations.AbortDockerContainerBatchUpdateOutputAccepted
import com.krillsson.sysapi.graphql.mutations.AbortDockerContainerBatchUpdateOutputFailed
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerInput
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerOutputFailed
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerOutputStarted
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainersInput
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainersOutputFailed
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainersOutputStarted
import com.krillsson.sysapi.graphql.mutations.UpdateHttpCheckInput
import com.krillsson.sysapi.graphql.mutations.UpdateDnsCheckInput
import com.krillsson.sysapi.graphql.mutations.UpdatePingCheckInput
import com.krillsson.sysapi.graphql.mutations.UpdateTcpCheckInput
import com.krillsson.sysapi.mqtt.MqttEventPayload
import com.krillsson.sysapi.notifications.MqttInfo
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.notifications.WebhookInfo
import com.krillsson.sysapi.notifications.webhook.WebhookPayload
import com.krillsson.sysapi.core.monitoring.MonitorStore
import com.krillsson.sysapi.core.monitoring.event.EventStore
import com.krillsson.sysapi.tls.CertificateNamesCreator
import com.krillsson.sysapi.tls.SelfSignedCertificateManager
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.JvmMetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.LogbackMetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.jdbc.DataSourcePoolMetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.startup.StartupTimeMetricsListenerAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.task.TaskExecutorMetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.web.tomcat.TomcatMetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.observation.graphql.GraphQlObservationAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.observation.web.client.HttpClientObservationsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.ssl.SslObservabilityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration
import org.springframework.boot.autoconfigure.graphql.data.GraphQlQueryByExampleAutoConfiguration
import org.springframework.boot.autoconfigure.graphql.data.GraphQlReactiveQueryByExampleAutoConfiguration
import org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration
import org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ImportRuntimeHints


@SpringBootApplication(
    scanBasePackages = ["com.krillsson.sysapi"],
    exclude = [
        MetricsAutoConfiguration::class,
        CompositeMeterRegistryAutoConfiguration::class,
        SimpleMetricsExportAutoConfiguration::class,
        JvmMetricsAutoConfiguration::class,
        SystemMetricsAutoConfiguration::class,
        TomcatMetricsAutoConfiguration::class,
        LogbackMetricsAutoConfiguration::class,
        DataSourcePoolMetricsAutoConfiguration::class,
        TaskExecutorMetricsAutoConfiguration::class,
        StartupTimeMetricsListenerAutoConfiguration::class,
        ObservationAutoConfiguration::class,
        GraphQlObservationAutoConfiguration::class,
        WebMvcObservationAutoConfiguration::class,
        HttpClientObservationsAutoConfiguration::class,
        SslObservabilityAutoConfiguration::class,
        GraphQlQueryByExampleAutoConfiguration::class,
        GraphQlReactiveQueryByExampleAutoConfiguration::class,
        WebClientAutoConfiguration::class,
        ClientHttpConnectorAutoConfiguration::class,
        CodecsAutoConfiguration::class,
        SpringDataWebAutoConfiguration::class,
        MultipartAutoConfiguration::class,
        WebSocketMessagingAutoConfiguration::class,
    ]
)
@ImportRuntimeHints(RuntimeHint::class)
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
    LinuxConfiguration::class,
    LogReaderConfiguration::class,
    MdnsConfiguration::class,
    MetricsConfiguration::class,
    MonitorConfiguration::class,
    UpsConfiguration::class,
    ProcessesConfiguration::class,
    SelfSignedCertificateConfiguration::class,
    UpdateCheckConfiguration::class,
    UpnpIgdConfiguration::class,
    UserConfiguration::class,
    WindowsConfiguration::class,
    YAMLConfigFile::class,
    DockerConfigFile::class,
    GenericEventStore.StoredGenericEvent.UpdateAvailable::class,
    GenericEventStore.StoredGenericEvent.MonitoredItemMissing::class,
    GenericEventStore.StoredGenericEvent.ContainerImageUpdateAvailable::class,
    ContainerImageUpdateAvailable::class,
    Notification.GenericEvent.ContainerImageUpdateAvailable::class,
    Notification.GenericEvent.ContainerImageUpdateDigest::class,
    ContainerImageUpdate::class,
    ImageUpdateStatus::class,
    ContainerUpdateEligibility::class,
    UpdateDockerContainerInput::class,
    UpdateDockerContainerOutputStarted::class,
    UpdateDockerContainerOutputFailed::class,
    ContainerUpdateStep::class,
    ImagePullLayer::class,
    ImagePullLayerPhase::class,
    DockerContainerUpdateStepChanged::class,
    DockerContainerUpdateImagePullProgress::class,
    DockerContainerUpdateSucceeded::class,
    DockerContainerUpdateFailed::class,
    DockerContainerUpdateJob::class,
    DockerContainerUpdateJobState::class,
    UpdateDockerContainersInput::class,
    UpdateDockerContainersOutputStarted::class,
    UpdateDockerContainersOutputFailed::class,
    AbortDockerContainerBatchUpdateInput::class,
    AbortDockerContainerBatchUpdateOutputAccepted::class,
    AbortDockerContainerBatchUpdateOutputFailed::class,
    DockerContainerBatchUpdateStarted::class,
    DockerContainerBatchUpdateContainerStarted::class,
    DockerContainerBatchUpdateContainerFinished::class,
    DockerContainerBatchUpdateContainerSkipped::class,
    DockerContainerBatchUpdateFinished::class,
    DockerContainerBatchUpdateJob::class,
    DockerContainerBatchUpdateJobState::class,
    EventStore.StoredEvent::class,
    MonitorStore.StoredMonitor::class,
    AuthConfig::class,
    SlowResolverWarningInstrumentation::class,
    NotificationsConfiguration::class,
    NotificationsConfiguration.NtfyConfiguration::class,
    NotificationsConfiguration.WebhookConfiguration::class,
    WebhookInfo::class,
    WebhookPayload::class,
    MqttConfiguration::class,
    MqttConfiguration.HomeAssistantConfiguration::class,
    MqttInfo::class,
    MqttEventPayload::class,
    CheckType::class,
    HttpMethod::class,
    HttpHeader::class,
    HttpCheck::class,
    TcpCheck::class,
    PingCheck::class,
    PingAvailability::class,
    DnsCheck::class,
    DnsRecordType::class,
    CheckResult::class,
    CheckResolution::class,
    CheckHistory::class,
    CheckHistoryPoint::class,
    CheckUptime::class,
    UptimeMetrics::class,
    UptimePeriod::class,
    UptimeDay::class,
    CreateHttpCheckInput::class,
    UpdateHttpCheckInput::class,
    OneOffHttpCheckInput::class,
    CreateTcpCheckInput::class,
    UpdateTcpCheckInput::class,
    OneOffTcpCheckInput::class,
    CreatePingCheckInput::class,
    UpdatePingCheckInput::class,
    OneOffPingCheckInput::class,
    CreateDnsCheckInput::class,
    UpdateDnsCheckInput::class,
    OneOffDnsCheckInput::class,
    HttpHeaderInput::class,
    DeleteCheckInput::class,
    SetCheckEnabledInput::class,
    RunCheckNowInput::class,
    CreateCheckSuccess::class,
    CreateCheckFailed::class,
    UpdateCheckSuccess::class,
    UpdateCheckFailed::class,
    DeleteCheckOutput::class,
    RunCheckNowSuccess::class,
    RunCheckNowFailed::class,
    FileBrowserConfiguration::class,
    FileBrowserAccess::class,
    FileBrowserLimits::class,
    FileEntry::class,
    FileEntryType::class,
    DirectoryListing::class,
    TextFileContent::class,
    FileBrowserError::class,
    SaveTextFileInput::class,
    SaveTextFileOutput::class,
    CopyFileInput::class,
    MoveFileInput::class,
    DeleteFileInput::class,
    CreateDirectoryInput::class,
    CreateDirectoryOutput::class,
    DirectoryListingConnection::class,
    FileEntryEdge::class,
    FileSearchInput::class,
    FileSearchResult::class,
    DirectorySize::class,
    TrashEntry::class,
    ArchiveEntry::class,
    ArchiveListing::class,
    FileOperationFailure::class,
    FileOperation::class,
    FileOperationType::class,
    FileOperationState::class,
    FileOperationOutput::class,
    CancelFileOperationInput::class,
    CopyFilesInput::class,
    MoveFilesInput::class,
    DeleteFilesInput::class,
    ExtractArchiveInput::class,
    CreateArchiveInput::class,
    MoveToTrashInput::class,
    MoveToTrashOutput::class,
    RestoreFromTrashInput::class,
    RestoreFromTrashOutput::class,
    EmptyTrashInput::class,
)
// https://www.graalvm.org/latest/reference-manual/native-image/dynamic-features/JNI/
// Failed to parse docker config.json
class SysAPIApplication {

    @Bean
    fun postStartupHook(
        selfSignedCertificateManager: SelfSignedCertificateManager,
        certificateNamesCreator: CertificateNamesCreator,
        config: YAMLConfigFile
    ): ApplicationRunner =
        ApplicationRunner {
            selfSignedCertificateManager.start(certificateNamesCreator, config.selfSignedCertificates)
        }
}

fun main(args: Array<String>) {
    System.setProperty("spring.graphql.websocket.path", "/graphql");
    runApplication<SysAPIApplication>(*args)
}
