package com.krillsson.sysapi.docker

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.AuthConfig
import com.github.dockerjava.api.model.ContainerConfig
import com.github.dockerjava.api.model.ContainerNetwork
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.Volume
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.ImagePullLayer
import com.krillsson.sysapi.core.domain.docker.ImagePullLayerPhase
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.measureTimeMillis
import java.util.concurrent.TimeUnit
import com.github.dockerjava.api.DockerClient as DockerJavaClient

class ContainerRecreator(private val client: DockerJavaClient) {

    companion object {
        private val LOGGER by logger()
        private const val PULL_TIMEOUT_MIN = 30L
        private const val PULL_PROGRESS_INTERVAL_MS = 1000L
        private const val REPLACED_CONTAINER_SUFFIX = "-old"
        private const val SHORT_CONTAINER_ID_LENGTH = 12
        private const val DEFAULT_NETWORK_MODE = "default"
        private const val UNTAGGED_IMAGE_REFERENCE = "<none>:<none>"
        private const val SHARED_NETWORK_NAMESPACE_MODE_PREFIX = "container:"
    }

    data class ImagePull(
        val repository: String,
        val tag: String,
        val authConfig: AuthConfig?
    )

    sealed interface Result {
        data class Success(val containerId: String, val replacedImageId: String?) : Result
        data class Failed(
            val step: ContainerUpdateStep,
            val reason: String,
            val rolledBack: Boolean
        ) : Result
    }

    interface Listener {
        fun onStep(step: ContainerUpdateStep)
        fun onPullProgress(layers: List<ImagePullLayer>)
    }

    fun recreate(containerId: String, pull: ImagePull?, listener: Listener): Result {
        listener.onStep(ContainerUpdateStep.INSPECTING_CONTAINER)
        val inspection = try {
            client.inspectContainerCmd(containerId).exec()
        } catch (e: RuntimeException) {
            return untouched(
                ContainerUpdateStep.INSPECTING_CONTAINER,
                "Container $containerId could not be inspected: ${e.message}"
            )
        }

        val config = inspection.config
        val hostConfig = inspection.hostConfig
        val name = inspection.name?.removePrefix("/")
        val image = config?.image
        if (config == null || hostConfig == null || name == null || image == null) {
            return untouched(
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
                return untouched(
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
            Result.Success(replacement.id, inspection.imageId)
        } catch (e: RuntimeException) {
            LOGGER.error("Recreating container $name failed, restoring the original", e)
            listener.onStep(ContainerUpdateStep.ROLLING_BACK)
            val rolledBack = restore(containerId, name, networks, stopped, renamed, replacementId)
            Result.Failed(step, "Recreating $name failed: ${e.message}", rolledBack)
        }
    }

    fun removeContainer(containerId: String): Boolean {
        return runCatching { client.removeContainerCmd(containerId).exec() }
            .onFailure { LOGGER.warn("Unable to remove container {}: {}", containerId, it.message) }
            .isSuccess
    }

    fun isImageOrphaned(imageId: String): Boolean {
        val tags = runCatching { client.inspectImageCmd(imageId).exec().repoTags }
            .getOrElse { return false }
            .orEmpty()
            .filterNot { it == UNTAGGED_IMAGE_REFERENCE }
        if (tags.isNotEmpty()) {
            return false
        }
        return runCatching {
            client.listContainersCmd().withShowAll(true).exec().none { it.imageId == imageId }
        }.getOrDefault(false)
    }

    fun removeImage(imageId: String): Boolean {
        return runCatching { client.removeImageCmd(imageId).exec() }
            .onSuccess { LOGGER.info("Removed image {} the replaced container was running", imageId) }
            .onFailure { LOGGER.warn("Unable to remove image {}: {}", imageId, it.message) }
            .isSuccess
    }

    private fun untouched(step: ContainerUpdateStep, reason: String) =
        Result.Failed(step, reason, rolledBack = true)

    private fun pullImage(pull: ImagePull, listener: Listener) {
        val callback = LayerProgressCallback(listener)
        val timedResult = measureTimeMillis {
            client.pullImageCmd(pull.repository)
                .withTag(pull.tag)
                .apply { pull.authConfig?.let { withAuthConfig(it) } }
                .exec(callback)
                .awaitCompletion(PULL_TIMEOUT_MIN, TimeUnit.MINUTES)
        }
        check(timedResult.second) {
            "Pulling ${pull.repository}:${pull.tag} did not finish within $PULL_TIMEOUT_MIN minutes"
        }
        callback.reportPulled()
        LOGGER.info("Took {} to pull {}:{}", "${timedResult.first.toInt()}ms", pull.repository, pull.tag)
    }

    /**
     * Inspect reports the image's own environment, labels and command merged with what the
     * container was created with, so anything the replaced image declared is dropped here rather
     * than pinned onto the replacement.
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
        // Inspect reports a hostname and the image's exposed ports for a container that runs on
        // another container's network, neither of which the daemon accepts on create in that mode.
        // Every other container's hostname is the short id it was given when it was created.
        if (!hostConfig.sharesNetworkNamespace()) {
            config.hostName.notInheritedFrom(replacedContainerId.take(SHORT_CONTAINER_ID_LENGTH))
                ?.let { create.withHostName(it) }
            config.exposedPorts?.let { create.withExposedPorts(*it) }
        }
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
        LOGGER.debug("Recreating {} from {} with hostConfig={}", name, image, hostConfig)
        return create.exec()
    }

    private fun HostConfig.sharesNetworkNamespace() =
        networkMode?.startsWith(SHARED_NETWORK_NAMESPACE_MODE_PREFIX) == true

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
     * docker-java derives the create call's networking config from the host config's network mode,
     * so only one network can be attached that way and the rest are connected afterwards.
     */
    private fun primaryNetworkName(hostConfig: HostConfig, networks: Map<String, ContainerNetwork>): String? {
        val mode = hostConfig.networkMode
        return when {
            mode == null || mode == DEFAULT_NETWORK_MODE -> networks.keys.firstOrNull()
            networks.containsKey(mode) -> mode
            else -> null
        }
    }

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
     * Docker reports a container's short id as one of its aliases, which must not follow it.
     */
    private fun ContainerNetwork.aliasesToKeep(replacedContainerId: String): List<String>? {
        val shortId = replacedContainerId.take(SHORT_CONTAINER_ID_LENGTH)
        return aliases?.filterNot { it == shortId }?.takeIf { it.isNotEmpty() }
    }

    private class LayerProgressCallback(private val listener: Listener) : PullImageResultCallback() {

        companion object {
            private const val OPENING_STATUS_PREFIX = "Pulling from"
            private val PHASES = mapOf(
                "Pulling fs layer" to ImagePullLayerPhase.WAITING,
                "Waiting" to ImagePullLayerPhase.WAITING,
                "Downloading" to ImagePullLayerPhase.DOWNLOADING,
                "Verifying Checksum" to ImagePullLayerPhase.DOWNLOADED,
                "Download complete" to ImagePullLayerPhase.DOWNLOADED,
                "Extracting" to ImagePullLayerPhase.EXTRACTING,
                "Pull complete" to ImagePullLayerPhase.COMPLETE,
                "Already exists" to ImagePullLayerPhase.ALREADY_PRESENT
            )
        }

        private class Layer(var status: String, var phase: ImagePullLayerPhase) {
            var downloadedBytes: Long? = null
            var downloadTotalBytes: Long? = null
            var extractedBytes: Long? = null
            var extractTotalBytes: Long? = null
            var extractElapsedSeconds: Long? = null
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

            val phaseChanged = synchronized(layers) {
                layers.getOrPut(id) { Layer(status, ImagePullLayerPhase.WAITING) }.record(status, item)
            }

            val now = System.currentTimeMillis()
            if (phaseChanged || now - reportedAt >= PULL_PROGRESS_INTERVAL_MS) {
                reportedAt = now
                reportProgress()
            }
        }

        /**
         * A pull that finds the content already on the machine can leave layers it announced
         * without ever reporting them as finished, so the last word on a pull that succeeded is
         * that every layer of it is present.
         */
        fun reportPulled() {
            synchronized(layers) {
                layers.values.forEach { layer ->
                    if (!layer.phase.isExtracted) {
                        layer.phase = ImagePullLayerPhase.COMPLETE
                        layer.downloadTotalBytes?.let { layer.downloadedBytes = it }
                        layer.extractTotalBytes?.let { layer.extractedBytes = it }
                    }
                }
            }
            reportProgress()
        }

        fun reportProgress() {
            val snapshot = synchronized(layers) {
                layers.map { (id, layer) ->
                    ImagePullLayer(
                        id = id,
                        status = layer.status,
                        phase = layer.phase,
                        downloadedBytes = layer.downloadedBytes,
                        downloadTotalBytes = layer.downloadTotalBytes,
                        extractedBytes = layer.extractedBytes,
                        extractTotalBytes = layer.extractTotalBytes,
                        extractElapsedSeconds = layer.extractElapsedSeconds
                    )
                }
            }
            if (snapshot.isNotEmpty()) {
                listener.onPullProgress(snapshot)
            }
        }

        /**
         * Docker stops reporting a layer's size once it is past a phase, so sizes are kept and
         * filled in as the layer moves on. A daemon using the containerd image store reports the
         * progress of an extraction as seconds elapsed instead of bytes, which is why extraction
         * bytes are only taken from a layer that has reported a size to measure them against.
         */
        private fun Layer.record(status: String, item: PullResponseItem): Boolean {
            this.status = status
            val previousPhase = phase
            phase = PHASES[status] ?: phase

            val current = item.progressDetail?.current
            val total = item.progressDetail?.total?.takeIf { it > 0 }
            when (phase) {
                ImagePullLayerPhase.DOWNLOADING -> {
                    total?.let { downloadTotalBytes = it }
                    downloadedBytes = current
                }

                ImagePullLayerPhase.EXTRACTING -> {
                    downloadTotalBytes?.let { downloadedBytes = it }
                    total?.let { extractTotalBytes = it }
                    if (extractTotalBytes == null) {
                        extractElapsedSeconds = current
                    } else {
                        extractedBytes = current
                        extractElapsedSeconds = null
                    }
                }

                ImagePullLayerPhase.COMPLETE -> {
                    downloadTotalBytes?.let { downloadedBytes = it }
                    extractTotalBytes?.let { extractedBytes = it }
                }

                ImagePullLayerPhase.DOWNLOADED -> downloadTotalBytes?.let { downloadedBytes = it }
                ImagePullLayerPhase.WAITING, ImagePullLayerPhase.ALREADY_PRESENT -> Unit
            }
            return phase != previousPhase
        }
    }
}
