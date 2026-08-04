package com.krillsson.sysapi

import SlowResolverWarningInstrumentation
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.core.DockerConfigFile
import com.krillsson.sysapi.config.*
import com.krillsson.sysapi.core.domain.docker.ContainerImageUpdate
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.ImagePullLayer
import com.krillsson.sysapi.core.domain.docker.ImageUpdateStatus
import com.krillsson.sysapi.core.genericevents.ContainerImageUpdateAvailable
import com.krillsson.sysapi.core.genericevents.GenericEventStore
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateFailed
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateImagePullProgress
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateStepChanged
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateSucceeded
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerInput
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerOutputFailed
import com.krillsson.sysapi.graphql.mutations.UpdateDockerContainerOutputStarted
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.core.monitoring.MonitorStore
import com.krillsson.sysapi.core.monitoring.event.EventStore
import com.krillsson.sysapi.tls.CertificateNamesCreator
import com.krillsson.sysapi.tls.SelfSignedCertificateManager
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ImportRuntimeHints


@SpringBootApplication(scanBasePackages = ["com.krillsson.sysapi"])
@ImportRuntimeHints(RuntimeHint::class)
@RegisterReflectionForBinding(
    CacheConfiguration::class,
    ConnectivityCheckConfiguration::class,
    ContainerUpdateCheckConfiguration::class,
    RegistryConfiguration::class,
    DockerConfiguration::class,
    HistoryConfiguration::class,
    HistoryPurgingConfiguration::class,
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
    ContainerImageUpdate::class,
    ImageUpdateStatus::class,
    UpdateDockerContainerInput::class,
    UpdateDockerContainerOutputStarted::class,
    UpdateDockerContainerOutputFailed::class,
    ContainerUpdateStep::class,
    ImagePullLayer::class,
    DockerContainerUpdateStepChanged::class,
    DockerContainerUpdateImagePullProgress::class,
    DockerContainerUpdateSucceeded::class,
    DockerContainerUpdateFailed::class,
    EventStore.StoredEvent::class,
    MonitorStore.StoredMonitor::class,
    AuthConfig::class,
    SlowResolverWarningInstrumentation::class,
    NotificationsConfiguration::class,
    NotificationsConfiguration.NtfyConfiguration::class,
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
