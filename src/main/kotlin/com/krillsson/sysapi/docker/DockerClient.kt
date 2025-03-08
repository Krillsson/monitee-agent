package com.krillsson.sysapi.docker

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientConfigDelegate
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.core.InvocationBuilder.AsyncResultCallback
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.docker.Command
import com.krillsson.sysapi.core.domain.docker.CommandType
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.system.Platform
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.measureTimeMillis
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
class DockerClient(
    private val dockerConfiguration: YAMLConfigFile,
    private val applicationObjectMapper: ObjectMapper,
    private val logLineParser: DockerLogLineParser,
    private val platform: Platform
) {

    companion object {
        val LOGGER by logger()
        const val READ_LOGS_COMMAND_TIMEOUT_SEC = 10L
    }

    private val defaultConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .apply {
            dockerConfiguration.docker.host?.let { host ->
                withDockerHost(host)
            }
        }
        .withDockerTlsVerify(false)
        .build()
    private val config: DockerClientConfig = object : DockerClientConfigDelegate(
        defaultConfig
    ) {
        override fun getObjectMapper(): ObjectMapper {
            return applicationObjectMapper
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
        }
    }

    private val httpClient: DockerHttpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig)
        .connectionTimeout(Duration.ofSeconds(15))
        .responseTimeout(Duration.ofSeconds(30))
        .maxConnections(100)
        .build()

    private val client = DockerClientImpl.getInstance(config, httpClient)


    fun performCommandWithContainer(command: Command): CommandResult {
        return try {
            val timedResult: Pair<Long, Void> = measureTimeMillis {
                when (command.commandType) {
                    CommandType.START -> client.startContainerCmd(command.id).exec()
                    CommandType.STOP -> client.stopContainerCmd(command.id).exec()
                    CommandType.PAUSE -> client.pauseContainerCmd(command.id).exec()
                    CommandType.UNPAUSE -> client.unpauseContainerCmd(command.id).exec()
                    CommandType.RESTART -> client.restartContainerCmd(command.id).exec()
                }
            }
            LOGGER.debug(
                "Took {} to perform {} with container {}",
                "${timedResult.first.toInt()}ms",
                command.commandType,
                command.id
            )
            CommandResult.Success
        } catch (e: RuntimeException) {
            CommandResult.Failed(e)
        }
    }


    fun listContainers(containersFilter: List<String> = emptyList()): List<Container> {
        val timedResult = measureTimeMillis {
            val command = if (containersFilter.isNotEmpty()) {
                client.listContainersCmd()
                    .withShowAll(true)
                    .withIdFilter(containersFilter)
            } else {
                client.listContainersCmd()
                    .withShowAll(true)
            }
            command.exec().map { container ->
                val inspection = client.inspectContainerCmd(container.id).exec()
                val volumes = if (inspection.volumes == null) emptyList() else inspection.volumes.asVolumeBindings()
                val config = inspection.config.asConfig(volumes)
                val health = inspection.state.health?.asHealth()
                container.asContainer(config, health)
            }
        }
        LOGGER.debug(
            "Took {} to fetch {} containers",
            "${timedResult.first.toInt()}ms",
            timedResult.second.size
        )
        return timedResult.second
    }

    fun containerStatistics(containerId: String): ContainerMetrics? {
        val timedResult = measureTimeMillis {
            var statistics: Statistics?
            val callback = AsyncResultCallback<Statistics>()
            client.statsCmd(containerId)
                .withNoStream(true)
                .exec(callback)
            try {
                // this call takes about ~1-2 sec since it's sleeping on the other end to measure CPU usage
                statistics = callback.awaitResult()
                callback.close()
                statistics.asStatistics(containerId, platform)
            } catch (exception: Exception) {
                LOGGER.error("Error while getting stats for $containerId", exception)
                null
            }
        }
        LOGGER.debug(
            "Took {} to fetch stats for container: {}",
            "${timedResult.first.toInt()}ms",
            containerId
        )
        return timedResult.second
    }

    fun readLogsForContainer(
        containerId: String,
        from: Instant?,
        to: Instant?
    ): List<String> {
        val timedResult = measureTimeMillis {
            val result = mutableListOf<String>()
            client.logContainerCmd(containerId)
                .withFollowStream(false)
                .withStdErr(true)
                .withStdOut(true)
                .withTimestamps(true)
                .apply { from?.let { withSince(from.toEpochMilli().div(1000).toInt()) } }
                .apply { to?.let { withUntil(to.toEpochMilli().div(1000).toInt()) } }
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame?) {
                        result.add(frame.toString())
                    }
                }).awaitCompletion(READ_LOGS_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            result
        }
        LOGGER.debug(
            "Took {} to fetch {} log lines",
            "${timedResult.first.toInt()}ms",
            timedResult.second.size
        )
        return timedResult.second
    }

    fun tailLogsForContainer(
        containerId: String
    ): Flux<DockerLogMessage> {
        return Flux.create { emitter ->
            val cmd = client.logContainerCmd(containerId)
                .withStdErr(true)
                .withStdOut(true)
                .withFollowStream(true)
                .withTail(0)
                .withTimestamps(true)
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame) {
                        emitter.next(logLineParser.parseFrame(frame))
                    }
                })
            emitter.onDispose {
                try {
                    cmd.close()
                } catch (e: Exception) {
                    emitter.error(e)
                }
            }
        }
    }

    fun readLogLinesForContainer(
        containerId: String,
        from: Instant? = null,
        to: Instant? = null,
        tail: Int? = null
    ): List<DockerLogMessage> {
        val timedResult = measureTimeMillis {
            val result = mutableListOf<DockerLogMessage>()
            client.logContainerCmd(containerId)
                .withFollowStream(false)
                .withStdErr(true)
                .withStdOut(true)
                .withTimestamps(true)
                .apply { tail?.let { withTail(tail) } }
                .apply { from?.let { withSince(from.toEpochMilli().div(1000).toInt()) } }
                .apply { to?.let { withUntil(to.toEpochMilli().div(1000).toInt()) } }
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame) {
                        result.add(logLineParser.parseFrame(frame))
                    }
                }).awaitCompletion(READ_LOGS_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            result
        }
        LOGGER.debug(
            "Took {} to fetch {} log lines",
            "${timedResult.first.toInt()}ms",
            timedResult.second.size
        )
        return timedResult.second
    }

    fun readFirstLogLineForContainer(
        containerId: String,
    ): DockerLogMessage? {
        val timedResult = measureTimeMillis {
            var result: DockerLogMessage? = null
            client.logContainerCmd(containerId)
                .withFollowStream(false)
                .withStdErr(true)
                .withStdOut(true)
                .withTimestamps(true)
                .exec(object : ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame) {
                        result = logLineParser.parseFrame(frame)
                        close()
                    }
                }).awaitCompletion(READ_LOGS_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            result
        }
        LOGGER.debug(
            "Took {} to fetch first log line",
            "${timedResult.first.toInt()}ms"
        )
        return timedResult.second
    }

    sealed interface PingResult {
        object Success : PingResult
        data class Fail(val throwable: Throwable) : PingResult
    }

    fun checkAvailability(): PingResult {
        return try {
            client.pingCmd().exec()
            PingResult.Success
        } catch (err: Throwable) {
            PingResult.Fail(err)
        }
    }

}






