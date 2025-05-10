package com.krillsson.sysapi

import SlowResolverWarningInstrumentation
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.connectivity.ConnectivityCheckService
import com.krillsson.sysapi.core.connectivity.ExternalIpAddressService
import com.krillsson.sysapi.core.domain.system.Platform
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.metrics.cache.Cache
import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultMetrics
import com.krillsson.sysapi.core.metrics.defaultimpl.DiskReadWriteRateMeasurementManager
import com.krillsson.sysapi.core.metrics.defaultimpl.NetworkUploadDownloadRateMeasurementManager
import com.krillsson.sysapi.core.metrics.linux.LinuxMetrics
import com.krillsson.sysapi.core.metrics.windows.WindowsMetrics
import com.krillsson.sysapi.graphql.domain.Meta
import com.krillsson.sysapi.graphql.scalars.ScalarTypes
import com.krillsson.sysapi.tls.CertificateNamesCreator
import com.krillsson.sysapi.tls.SelfSignedCertificateManager
import com.krillsson.sysapi.updatechecker.GithubApiService
import com.krillsson.sysapi.util.FileSystem
import com.krillsson.sysapi.util.asOperatingSystem
import com.krillsson.sysapi.util.asPlatform
import com.krillsson.sysapi.util.logger
import org.apache.catalina.connector.Connector
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.EnableTransactionManagement
import oshi.SystemInfo
import oshi.hardware.HardwareAbstractionLayer
import oshi.software.os.OperatingSystem
import oshi.util.GlobalConfig
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.File
import java.time.Clock
import java.util.concurrent.Executor

const val USER_CONFIG = "configuration.yml"
const val SPRING_CONFIG = "application.properties"

@Configuration
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
class SysApiConfiguration : AsyncConfigurer {

    val logger by logger()

    @Bean
    fun graphQlCustomizer(
        slowResolverWarningInstrumentation: SlowResolverWarningInstrumentation,
        configuration: YAMLConfigFile,
    ): GraphQlSourceBuilderCustomizer {
        return GraphQlSourceBuilderCustomizer { builder ->
            if (configuration.graphQl.instrumentation) {
                builder.instrumentation(listOf(slowResolverWarningInstrumentation))
            }
        }
    }

    @Bean
    fun slowResolverWarningInstrumentation() = SlowResolverWarningInstrumentation()

    @Bean
    fun userConfigFile(): YAMLConfigFile {
        val yamlParser = ObjectMapper(YAMLFactory())
        yamlParser.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        yamlParser.findAndRegisterModules()
        val configFile = File(FileSystem.config, USER_CONFIG)
        val fallbackConfigFile = javaClass.getResourceAsStream("/config/$USER_CONFIG")
        return if (configFile.exists()) {
            yamlParser.readValue(configFile, YAMLConfigFile::class.java)
        } else {
            logger.warn("Config file is missing at ${configFile.absoluteFile}. Using default...")
            yamlParser.readValue(fallbackConfigFile, YAMLConfigFile::class.java)
        }
    }

    @Suppress("DEPRECATION")
    @Bean
    fun tomcatSslStoreCustomizer(
        selfSignedCertificateManager: SelfSignedCertificateManager,
        certificateNamesCreator: CertificateNamesCreator,
        config: YAMLConfigFile
    ): WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
        if (!FileSystem.data.isDirectory) {
            logger.info("Attempting to create {}", FileSystem.data)
            assert(FileSystem.data.mkdir()) { "Unable to create ${FileSystem.data}" }
        }
        val store = selfSignedCertificateManager.start(certificateNamesCreator, config.selfSignedCertificates)

        return WebServerFactoryCustomizer { tomcat: TomcatServletWebServerFactory ->
            val ssl = tomcat.ssl
            ssl.keyStore = store.path
            ssl.keyAlias = SelfSignedCertificateManager.KEYSTORE_ENTRY_ALIAS
            ssl.keyStorePassword = SelfSignedCertificateManager.KEYSTORE_PASSWORD
            ssl.keyPassword = SelfSignedCertificateManager.KEYSTORE_PASSWORD
        }
    }

    @Bean
    fun cookieProcessorCustomizer(@Value("\${http.port}") httpPort: Int): WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
        return WebServerFactoryCustomizer<TomcatServletWebServerFactory> { factory: TomcatServletWebServerFactory ->
            // also listen on http
            val connector = Connector()
            connector.setPort(httpPort)
            factory.addAdditionalTomcatConnectors(connector)
        }
    }

    @Bean
    fun metrics(
        configuration: YAMLConfigFile,
        windowsMetricsProvider: ObjectProvider<WindowsMetrics>,
        linuxMetricsProvider: ObjectProvider<LinuxMetrics>,
        defaultMetrics: DefaultMetrics,
        hal: HardwareAbstractionLayer,
        operatingSystem: OperatingSystem,
        platform: Platform,
        diskReadWriteRateMeasurementManager: DiskReadWriteRateMeasurementManager,
        networkUploadDownloadRateMeasurementManager: NetworkUploadDownloadRateMeasurementManager,
        connectivityCheckService: ConnectivityCheckService
    ): Metrics {

        val platformSpecific = when {
            platform == Platform.WINDOWS && (configuration.windows.enableOhmJniWrapper) -> {
                logger.info("Windows detected")
                val windowsMetrics = windowsMetricsProvider.getObject()
                if (windowsMetrics.prerequisitesFilled()) {
                    windowsMetrics
                } else {
                    logger.error("Unable to use Windows specific implementation: falling through to default one")
                    defaultMetrics
                }
            }

            platform == Platform.LINUX -> {
                linuxMetricsProvider.getObject()
            }

            else -> defaultMetrics
        }
        platformSpecific.initialize()
        return if (configuration.metricsConfig.cache.enabled) {
            Cache.wrap(
                platformSpecific,
                configuration.metricsConfig.cache,
                platform,
                operatingSystem.asOperatingSystem()
            )
        } else {
            platformSpecific
        }
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun systemInfo(): SystemInfo {
        GlobalConfig.set("oshi.os.windows.loadaverage", true)
        return SystemInfo()
    }


    @Bean
    fun hal(systemInfo: SystemInfo) = systemInfo.hardware

    @Bean
    fun os(systemInfo: SystemInfo) = systemInfo.operatingSystem

    @Bean
    fun centralProcessor(hal: HardwareAbstractionLayer) = hal.processor

    @Bean
    fun operatingSystem(systemInfo: SystemInfo) = systemInfo.operatingSystem.asOperatingSystem()

    @Bean
    fun meta(os: OperatingSystem) = Meta(
        version = BuildConfig.APP_VERSION,
        buildDate = BuildConfig.BUILD_TIME.toString(),
        processId = os.processId,
        endpoints = emptyList(),
    )

    @Bean
    fun platform() = SystemInfo.getCurrentPlatform().asPlatform()

    @Bean
    fun githubApi(yamlConfigFile: YAMLConfigFile, mapper: ObjectMapper): GithubApiService {
        return Retrofit.Builder()
            .baseUrl(yamlConfigFile.updateCheck.address)
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .build()
            .create(GithubApiService::class.java)
    }

    @Bean
    fun externalIpAddressService(yamlConfigFile: YAMLConfigFile): ExternalIpAddressService {
        return Retrofit.Builder()
            .baseUrl(yamlConfigFile.connectivityCheck.address)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(ExternalIpAddressService::class.java)
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun configureGraphQLRuntimeWiring(): RuntimeWiringConfigurer {
        return RuntimeWiringConfigurer { builder ->
            ScalarTypes.scalars.forEach {
                builder.scalar(it)
            }
        }
    }

    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 42
        executor.queueCapacity = 11
        executor.setThreadNamePrefix("@Async-")
        executor.initialize()
        return executor
    }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { ex, method, params ->
            logger.error("$method with parameters $params encountered an uncaught exception", ex)
        }
    }
}