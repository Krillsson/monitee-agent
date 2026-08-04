package com.krillsson.sysapi.docker

import com.github.dockerjava.api.model.AuthConfig
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.docker.updates.ContainerUpdateChecker
import com.krillsson.sysapi.docker.updates.ImageReference
import com.krillsson.sysapi.docker.updates.RegistryClient
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service

/**
 * Replaces a container with one running the current image behind its reference, and moves
 * everything that is keyed on the container id over to the replacement.
 *
 * Split in two so the caller can answer a request before the work starts: [prepare] does
 * everything that can fail immediately, [recreate] does the part worth watching.
 */
@Service
class ContainerRecreateService(
    private val containerService: ContainerService,
    private val dockerClient: DockerClient,
    private val registryClient: RegistryClient,
    private val containerUpdateChecker: ContainerUpdateChecker,
    private val containersHistoryRepository: ContainersHistoryRepository,
    private val monitorManager: MonitorManager,
    private val eventManager: EventManager
) {
    companion object {
        private const val SWARM_SERVICE_LABEL = "com.docker.swarm.service.id"
        private const val COMPOSE_PROJECT_LABEL = "com.docker.compose.project"
    }

    private val logger by logger()

    sealed interface Preparation {
        data class Ready(
            val containerId: String,
            val name: String,
            val imageRef: String,
            val composeProject: String?,
            val pull: DockerClient.ImagePull?
        ) : Preparation

        data class Rejected(val reason: String) : Preparation
        object Unavailable : Preparation
    }

    fun prepare(containerId: String, pullImage: Boolean): Preparation {
        if (containerService.status != Status.Available) {
            return Preparation.Unavailable
        }

        val container = containerService.container(containerId)
            ?: return Preparation.Rejected("No container with id $containerId")
        val name = container.names.firstOrNull()?.removePrefix("/") ?: containerId

        if (container.labels.containsKey(SWARM_SERVICE_LABEL)) {
            return Preparation.Rejected("$name is managed by Swarm, update its service instead")
        }

        val pull = if (pullImage) {
            val reference = ImageReference.parse(container.image)
                ?: return Preparation.Rejected("${container.image} is not a registry image reference")
            reference.asImagePull()
        } else {
            null
        }

        return Preparation.Ready(
            containerId = containerId,
            name = name,
            imageRef = container.image,
            composeProject = container.labels[COMPOSE_PROJECT_LABEL],
            pull = pull
        )
    }

    fun recreate(prepared: Preparation.Ready, listener: DockerClient.RecreateListener): RecreateContainerResult {
        logger.info("Recreating container {} ({})", prepared.name, prepared.containerId)
        return when (val result = dockerClient.recreateContainer(prepared.containerId, prepared.pull, listener)) {
            is DockerClient.RecreateResult.Failed -> RecreateContainerResult.Failed(
                result.step,
                result.reason,
                result.rolledBack
            )

            is DockerClient.RecreateResult.Success -> {
                followContainer(prepared.containerId, result.containerId, listener)
                RecreateContainerResult.Success(result.containerId, prepared.composeProject)
            }
        }
    }

    /**
     * Both containers exist while this runs, so nothing that is keyed on a container id ever points
     * at one that is gone: the replacement takes over first and only then is the original removed.
     */
    private fun followContainer(
        oldContainerId: String,
        newContainerId: String,
        listener: DockerClient.RecreateListener
    ) {
        listener.onStep(ContainerUpdateStep.MOVING_MONITORS_AND_HISTORY)
        containerService.invalidateContainersCache()
        containerService.container(newContainerId)?.let { replacement ->
            containerUpdateChecker.containerReplaced(oldContainerId, replacement)
        }
        monitorManager.replaceMonitoredItemId(oldContainerId, newContainerId)
        eventManager.replaceMonitoredItemId(oldContainerId, newContainerId)
        containersHistoryRepository.moveHistoryToContainerId(oldContainerId, newContainerId)

        listener.onStep(ContainerUpdateStep.REMOVING_REPLACED_CONTAINER)
        dockerClient.removeContainer(oldContainerId)
        containerService.invalidateContainersCache()
    }

    private fun ImageReference.asImagePull(): DockerClient.ImagePull {
        val repository = if (registry == ImageReference.DOCKER_HUB_REGISTRY) repository else "$registry/$repository"
        return DockerClient.ImagePull(
            repository = repository,
            tag = digest ?: tag,
            authConfig = registryClient.credentialsFor(this)?.let { credentials ->
                AuthConfig()
                    .withRegistryAddress(if (registry == ImageReference.DOCKER_HUB_REGISTRY) AuthConfig.DEFAULT_SERVER_ADDRESS else registry)
                    .withUsername(credentials.username)
                    .withPassword(credentials.password)
            }
        )
    }
}
