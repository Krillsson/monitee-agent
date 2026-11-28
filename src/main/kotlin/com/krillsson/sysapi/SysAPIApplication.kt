package com.krillsson.sysapi

import com.krillsson.sysapi.config.*
import com.krillsson.sysapi.tls.CertificateNamesCreator
import com.krillsson.sysapi.tls.SelfSignedCertificateManager
import com.krillsson.sysapi.launcher.LaunchCommand
import com.krillsson.sysapi.launcher.LauncherHandshake
import com.krillsson.sysapi.launcher.NativeSelfTest
import com.krillsson.sysapi.launcher.StartupFailure
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
import org.springframework.core.NativeDetector
import kotlin.system.exitProcess


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
    val inNativeImage = NativeDetector.inNativeImage()
    LaunchCommand.run(args, System.out, inNativeImage) { NativeSelfTest.forThisHost() }?.let { exitProcess(it) }
    LauncherHandshake.fromEnvironment(inNativeImage).exitWhenLauncherExits()
    System.setProperty("spring.graphql.websocket.path", "/graphql");
    try {
        runApplication<SysAPIApplication>(*args) {
            setDefaultProperties(ConfigurationFileLocation.defaultProperties())
        }
    } catch (failure: Throwable) {
        val exitCode = StartupFailure.exitCode(failure, inNativeImage) ?: throw failure
        System.err.println("Startup failed on a native-image limitation, exiting with $exitCode so the launcher can fall back to the JVM")
        exitProcess(exitCode)
    }
}
