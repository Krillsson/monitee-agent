package com.krillsson.sysapi.docker

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.ContainerConfig
import com.github.dockerjava.api.model.ContainerNetwork
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.api.model.Volume
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
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.ImagePullLayer
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
        private const val PULL_IMAGE_COMMAND_TIMEOUT_MIN = 30L
        private const val REPLACED_CONTAINER_SUFFIX = "-old"
        private const val SHORT_CONTAINER_ID_LENGTH = 12
        private const val DEFAULT_NETWORK_MODE = "default"
        private const val PULL_PROGRESS_INTERVAL_MS = 1000L
    }

    private val defaultConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .apply {
            dockerConfiguration.docker.host?.let { host ->
                withDockerHost(host)
            }
        }
        .withDockerTlsVerify(false)
        .build()

    /**
     * docker-java writes the create and network commands out as the request body itself, and its
     * own mapper allows the empty objects that the exposed ports of a container serialize into.
     * The application mapper does not, so it is copied rather than reconfigured in place.
     */
    private val dockerObjectMapper: ObjectMapper = applicationObjectMapper.copy()
        .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

    private val config: DockerClientConfig = object : DockerClientConfigDelegate(
        defaultConfig
    ) {
        override fun getObjectMapper(): ObjectMapper {
            return dockerObjectMapper
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

    data class ImageInspection(
        val repoDigests: List<String>,
        val repoTags: List<String>
    )

    fun inspectImage(imageId: String): ImageInspection? {
        return try {
            val response = client.inspectImageCmd(imageId).exec()
            ImageInspection(
                response.repoDigests.orEmpty(),
                response.repoTags.orEmpty()
            )
        } catch (e: RuntimeException) {
            LOGGER.debug("Unable to inspect image {}: {}", imageId, e.message)
            null
        }
    }

    data class ImagePull(
        val repository: String,
        val tag: String,
        val authConfig: AuthConfig?
    )

    sealed interface RecreateResult {
        data class Success(val containerId: String) : RecreateResult
        data class Failed(
            val step: ContainerUpdateStep,
            val reason: String,
            val rolledBack: Boolean
        ) : RecreateResult
    }

    /**
     * Reports what the recreate is doing while it does it. Called from the thread running the
     * update, and for pull progress from the thread docker-java reads the pull stream on.
     */
    interface RecreateListener {
        fun onStep(step: ContainerUpdateStep)
        fun onPullProgress(layers: List<ImagePullLayer>)
    }

    /**
     * Replaces a container with one created from the same configuration, running the image the
     * reference now points at. Docker cannot update a container in place, so the replacement is
     * created from the inspect response of the original and gets a new container id.
     *
     * The image is pulled before anything is touched, so a failed pull leaves the container alone.
     * The original is renamed out of the way instead of removed, and its networks are disconnected
     * by force, which releases the static addresses and aliases the replacement claims again.
     * Anything that fails after the rename puts the original back.
     *
     * The original is left behind as `<name>-old`, stopped: the caller removes it once everything
     * keyed on the old container id has followed it to the new one.
     */
    fun recreateContainer(containerId: String, pull: ImagePull?, listener: RecreateListener): RecreateResult {
        listener.onStep(ContainerUpdateStep.INSPECTING_CONTAINER)
        val inspection = try {
            client.inspectContainerCmd(containerId).exec()
        } catch (e: RuntimeException) {
            return failedBeforeAnythingChanged(
                ContainerUpdateStep.INSPECTING_CONTAINER,
                "Container $containerId could not be inspected: ${e.message}"
            )
        }

        val config = inspection.config
        val hostConfig = inspection.hostConfig
        val name = inspection.name?.removePrefix("/")
        val image = config?.image
        if (config == null || hostConfig == null || name == null || image == null) {
            return failedBeforeAnythingChanged(
                ContainerUpdateStep.INSPECTING_CONTAINER,
                "Container $containerId does not report the configuration it was created with"
            )
        }

        val replacedImageConfig = inspection.imageId?.let { imageId ->
            runCatching { client.inspectImageCmd(imageId).exec().config }.getOrNull()
        }

        if (pull != null) {
            listener.onStep(ContainerUpdateStep.PULLING_IMAGE)
            try {
                pullImage(pull, listener)
            } catch (e: Exception) {
                return failedBeforeAnythingChanged(
                    ContainerUpdateStep.PULLING_IMAGE,
                    "Pulling ${pull.repository}:${pull.tag} failed: ${e.message}"
                )
            }
        }

        val networks = inspection.networkSettings?.networks.orEmpty()
        val primaryNetwork = primaryNetworkName(hostConfig, networks)
        val wasRunning = inspection.state?.running == true

        var step = ContainerUpdateStep.STOPPING_CONTAINER
        var stopped = false
        var renamed = false
        var replacementId: String? = null
        return try {
            if (wasRunning) {
                listener.onStep(ContainerUpdateStep.STOPPING_CONTAINER)
                client.stopContainerCmd(containerId).exec()
                stopped = true
            }

            step = ContainerUpdateStep.RENAMING_CONTAINER
            listener.onStep(step)
            client.renameContainerCmd(containerId).withName("$name$REPLACED_CONTAINER_SUFFIX").exec()
            renamed = true

            if (networks.isNotEmpty()) {
                step = ContainerUpdateStep.DISCONNECTING_NETWORKS
                listener.onStep(step)
                networks.keys.forEach { network ->
                    client.disconnectFromNetworkCmd()
                        .withContainerId(containerId)
                        .withNetworkId(network)
                        .withForce(true)
                        .exec()
                }
            }

            step = ContainerUpdateStep.CREATING_CONTAINER
            listener.onStep(step)
            val replacement = createContainer(
                name,
                image,
                config,
                replacedImageConfig,
                hostConfig,
                networks[primaryNetwork],
                containerId
            )
            replacementId = replacement.id

            val remainingNetworks = networks.filterKeys { it != primaryNetwork }
            if (remainingNetworks.isNotEmpty()) {
                step = ContainerUpdateStep.CONNECTING_NETWORKS
                listener.onStep(step)
                remainingNetworks.forEach { (network, endpoint) ->
                    client.connectToNetworkCmd()
                        .withContainerId(replacement.id)
                        .withNetworkId(network)
                        .withContainerNetwork(endpoint.asEndpointConfig(containerId))
                        .exec()
                }
            }

            if (wasRunning) {
                step = ContainerUpdateStep.STARTING_CONTAINER
                listener.onStep(step)
                client.startContainerCmd(replacement.id).exec()
            }
            LOGGER.info("Recreated container {} as {} running {}", name, replacement.id, image)
            RecreateResult.Success(replacement.id)
        } catch (e: RuntimeException) {
            LOGGER.error("Recreating container $name failed, restoring the original", e)
            listener.onStep(ContainerUpdateStep.ROLLING_BACK)
            val rolledBack = restore(containerId, name, networks, stopped, renamed, replacementId)
            RecreateResult.Failed(step, "Recreating $name failed: ${e.message}", rolledBack)
        }
    }

    private fun failedBeforeAnythingChanged(step: ContainerUpdateStep, reason: String) =
        RecreateResult.Failed(step, reason, rolledBack = true)

    fun removeContainer(containerId: String): Boolean {
        return runCatching { client.removeContainerCmd(containerId).exec() }
            .onFailure { LOGGER.warn("Unable to remove container {}: {}", containerId, it.message) }
            .isSuccess
    }

    private fun pullImage(pull: ImagePull, listener: RecreateListener) {
        val callback = LayerProgressCallback(listener)
        val timedResult = measureTimeMillis {
            client.pullImageCmd(pull.repository)
                .withTag(pull.tag)
                .apply { pull.authConfig?.let { withAuthConfig(it) } }
                .exec(callback)
                .awaitCompletion(PULL_IMAGE_COMMAND_TIMEOUT_MIN, TimeUnit.MINUTES)
        }
        callback.reportProgress()
        check(timedResult.second) {
            "Pulling ${pull.repository}:${pull.tag} did not finish within $PULL_IMAGE_COMMAND_TIMEOUT_MIN minutes"
        }
        LOGGER.info("Took {} to pull {}:{}", "${timedResult.first.toInt()}ms", pull.repository, pull.tag)
    }

    /**
     * Docker reports the pull as a stream of per-layer status lines, several a second per layer
     * while they download. They are collapsed into the latest state of every layer and handed on
     * at a fixed rate, so a subscriber sees smooth progress without one event per line.
     *
     * Only the download counts: a layer reports byte counts again while it extracts, and taking
     * those at face value makes the totals collapse and start over halfway through. A size is
     * kept once it is known and the layer is counted as fully downloaded as soon as it moves past
     * downloading, so the numbers only ever grow. Layers that were already present report no size
     * at all and are left out of the totals.
     *
     * Lines that carry no layer id — the digest and the closing status — are not progress, and
     * neither is the opening line, whose id is the tag being pulled rather than a layer.
     */
    private class LayerProgressCallback(private val listener: RecreateListener) : PullImageResultCallback() {

        companion object {
            private val UNFINISHED_LAYER_STATUSES = setOf("Pulling fs layer", "Waiting", "Downloading")
            private const val DOWNLOADING_STATUS = "Downloading"
            private const val OPENING_STATUS_PREFIX = "Pulling from"
        }

        private class Layer(var status: String) {
            var currentBytes: Long? = null
            var totalBytes: Long? = null
        }

        private val layers = LinkedHashMap<String, Layer>()
        private var reportedAt = 0L

        override fun onNext(item: PullResponseItem) {
            super.onNext(item)
            val id = item.id
            val status = item.status
            if (id == null || status == null || status.startsWith(OPENING_STATUS_PREFIX)) {
                return
            }

            synchronized(layers) {
                val layer = layers.getOrPut(id) { Layer(status) }
                layer.status = status
                when {
                    status == DOWNLOADING_STATUS -> {
                        item.progressDetail?.total?.takeIf { it > 0 }?.let { layer.totalBytes = it }
                        item.progressDetail?.current?.let { layer.currentBytes = it }
                    }

                    status !in UNFINISHED_LAYER_STATUSES -> layer.currentBytes = layer.totalBytes
                    else -> Unit
                }
            }

            val now = System.currentTimeMillis()
            if (now - reportedAt >= PULL_PROGRESS_INTERVAL_MS) {
                reportedAt = now
                reportProgress()
            }
        }

        fun reportProgress() {
            val snapshot = synchronized(layers) {
                layers.map { (id, layer) -> ImagePullLayer(id, layer.status, layer.currentBytes, layer.totalBytes) }
            }
            if (snapshot.isNotEmpty()) {
                listener.onPullProgress(snapshot)
            }
        }
    }

    /**
     * The inspect response does not separate what the container was asked for from what its image
     * supplied: the environment, labels, command and health check it reports are the image's
     * defaults with the user's additions merged in. Carrying all of that over would pin the
     * replacement to the image being replaced — a new `NGINX_VERSION`, `PATH` or entry point would
     * be overwritten by the old one — so anything the replaced image already declared is dropped
     * and left to the new image.
     */
    private fun createContainer(
        name: String,
        image: String,
        config: ContainerConfig,
        replacedImageConfig: ContainerConfig?,
        hostConfig: HostConfig,
        endpoint: ContainerNetwork?,
        replacedContainerId: String
    ): CreateContainerResponse {
        val create = client.createContainerCmd(image)
            .withName(name)
            .withHostConfig(hostConfig)
        config.hostName?.let { create.withHostName(it) }
        config.domainName?.let { create.withDomainName(it) }
        config.user.notInheritedFrom(replacedImageConfig?.user)?.let { create.withUser(it) }
        config.attachStdin?.let { create.withAttachStdin(it) }
        config.attachStdout?.let { create.withAttachStdout(it) }
        config.attachStderr?.let { create.withAttachStderr(it) }
        config.tty?.let { create.withTty(it) }
        config.stdinOpen?.let { create.withStdinOpen(it) }
        config.stdInOnce?.let { create.withStdInOnce(it) }
        config.env.withoutEntriesOf(replacedImageConfig?.env)?.let { create.withEnv(it) }
        config.cmd.notInheritedFrom(replacedImageConfig?.cmd)?.let { create.withCmd(*it) }
        config.entrypoint.notInheritedFrom(replacedImageConfig?.entrypoint)?.let { create.withEntrypoint(*it) }
        config.exposedPorts?.let { create.withExposedPorts(*it) }
        config.volumes?.let { volumes -> create.withVolumes(volumes.keys.map { Volume(it) }) }
        config.labels.withoutEntriesOf(replacedImageConfig?.labels)?.let { create.withLabels(it) }
        config.healthcheck.notInheritedFrom(replacedImageConfig?.healthcheck)?.let { create.withHealthcheck(it) }
        config.workingDir.notInheritedFrom(replacedImageConfig?.workingDir)?.let { create.withWorkingDir(it) }
        config.networkDisabled?.let { create.withNetworkDisabled(it) }

        endpoint?.let { network ->
            network.ipamConfig?.ipv4Address?.let { create.withIpv4Address(it) }
            network.ipamConfig?.ipv6Address?.let { create.withIpv6Address(it) }
            network.aliasesToKeep(replacedContainerId)?.let { create.withAliases(it) }
        }
        return create.exec()
    }

    private fun <T> T?.notInheritedFrom(imageValue: T?): T? = takeIf { it != imageValue }

    private fun Array<String>?.notInheritedFrom(imageValue: Array<String>?): Array<String>? =
        takeIf { it != null && !it.contentEquals(imageValue) }

    private fun Array<String>?.withoutEntriesOf(imageValues: Array<String>?): List<String>? {
        val inherited = imageValues?.toSet().orEmpty()
        return this?.filterNot { inherited.contains(it) }
    }

    private fun Map<String, String>?.withoutEntriesOf(imageValues: Map<String, String>?): Map<String, String>? {
        val inherited = imageValues.orEmpty()
        return this?.filterNot { (key, value) -> inherited[key] == value }
    }

    /**
     * Undoes as much of a failed recreate as it can, and reports whether the original container is
     * back the way it was. Every step is attempted even when an earlier one fails, so a container
     * that cannot be renamed back is still restarted.
     */
    private fun restore(
        containerId: String,
        name: String,
        networks: Map<String, ContainerNetwork>,
        stopped: Boolean,
        renamed: Boolean,
        replacementId: String?
    ): Boolean {
        var restored = true
        replacementId?.let { id ->
            runCatching { client.stopContainerCmd(id).exec() }
            runCatching { client.removeContainerCmd(id).exec() }
                .onFailure {
                    restored = false
                    LOGGER.error("Unable to remove the half created container {}: {}", id, it.message)
                }
        }
        if (renamed) {
            runCatching { client.renameContainerCmd(containerId).withName(name).exec() }
                .onFailure {
                    restored = false
                    LOGGER.error("Unable to name {} back to {}: {}", containerId, name, it.message)
                }
            networks.forEach { (network, endpoint) ->
                runCatching {
                    client.connectToNetworkCmd()
                        .withContainerId(containerId)
                        .withNetworkId(network)
                        .withContainerNetwork(endpoint.asEndpointConfig(containerId))
                        .exec()
                }.onFailure {
                    restored = false
                    LOGGER.error("Unable to reconnect {} to {}: {}", name, network, it.message)
                }
            }
        }
        if (stopped) {
            runCatching { client.startContainerCmd(containerId).exec() }
                .onFailure {
                    restored = false
                    LOGGER.error("Unable to start {} again: {}", name, it.message)
                }
        }
        return restored
    }

    /**
     * The create call attaches exactly one network, the one the host configuration names, and
     * creating with none at all silently lands the container on the default bridge with an address
     * of Docker's choosing. The remaining networks are connected once the container exists.
     */
    private fun primaryNetworkName(hostConfig: HostConfig, networks: Map<String, ContainerNetwork>): String? {
        val mode = hostConfig.networkMode
        return when {
            mode == null || mode == DEFAULT_NETWORK_MODE -> networks.keys.firstOrNull()
            networks.containsKey(mode) -> mode
            else -> null
        }
    }

    /**
     * Only the parts of an endpoint that have to be asked for again: a static address and the
     * aliases. The rest of the inspect response is state the engine assigns by itself.
     */
    private fun ContainerNetwork.asEndpointConfig(replacedContainerId: String): ContainerNetwork {
        val aliases = aliasesToKeep(replacedContainerId)
        val ipam = ipamConfig
        return ContainerNetwork().apply {
            aliases?.let { withAliases(it) }
            ipam?.let {
                withIpamConfig(
                    ContainerNetwork.Ipam()
                        .withIpv4Address(it.ipv4Address)
                        .withIpv6Address(it.ipv6Address)
                )
            }
        }
    }

    /**
     * Docker resolves a container by the first twelve characters of its id, and reports that as an
     * alias. Carrying it over would name the replacement after the container it replaces.
     */
    private fun ContainerNetwork.aliasesToKeep(replacedContainerId: String): List<String>? {
        val shortId = replacedContainerId.take(SHORT_CONTAINER_ID_LENGTH)
        return aliases?.filterNot { it == shortId }?.takeIf { it.isNotEmpty() }
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






