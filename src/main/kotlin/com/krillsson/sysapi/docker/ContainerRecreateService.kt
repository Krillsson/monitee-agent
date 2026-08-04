package com.krillsson.sysapi.docker

import com.github.dockerjava.api.model.AuthConfig
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.docker.updates.ContainerUpdateChecker
import com.krillsson.sysapi.docker.updates.ImageReference
import com.krillsson.sysapi.docker.updates.RegistryClient
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service

/**
 * Replaces a container with one running the current image behind its reference, and moves
 * everything that is keyed on the container id over to the replacement.
 */
@Service
class ContainerRecreateService(
    private val containerService: ContainerService,
    private val dockerClient: DockerClient,
    private val registryClient: RegistryClient,
    private val containerUpdateChecker: ContainerUpdateChecker,
    private val containersHistoryRepository: ContainersHistoryRepository,
    private val monitorManager: MonitorManager
) {
    companion object {
        private const val SWARM_SERVICE_LABEL = "com.docker.swarm.service.id"
        private const val COMPOSE_PROJECT_LABEL = "com.docker.compose.project"
    }

    private val logger by logger()

    fun recreateContainer(containerId: String, pullImage: Boolean): RecreateContainerResult {
        if (containerService.status != Status.Available) {
            return RecreateContainerResult.Unavailable
        }

        val container = containerService.container(containerId)
            ?: return RecreateContainerResult.Failed("No container with id $containerId")
        val name = container.names.firstOrNull()?.removePrefix("/") ?: containerId

        if (container.labels.containsKey(SWARM_SERVICE_LABEL)) {
            return RecreateContainerResult.Failed("$name is managed by Swarm, update its service instead")
        }

        val pull = if (pullImage) {
            val reference = ImageReference.parse(container.image)
                ?: return RecreateContainerResult.Failed("${container.image} is not a registry image reference")
            reference.asImagePull()
        } else {
            null
        }

        logger.info("Recreating container {} ({})", name, containerId)
        return when (val result = dockerClient.recreateContainer(containerId, pull)) {
            is DockerClient.RecreateResult.Failed -> RecreateContainerResult.Failed(result.reason)
            is DockerClient.RecreateResult.Success -> {
                followContainer(containerId, result.containerId)
                RecreateContainerResult.Success(result.containerId, container.labels[COMPOSE_PROJECT_LABEL])
            }
        }
    }

    /**
     * Both containers exist while this runs, so nothing that is keyed on a container id ever points
     * at one that is gone: the replacement takes over first and only then is the original removed.
     */
    private fun followContainer(oldContainerId: String, newContainerId: String) {
        containerService.invalidateContainersCache()
        containerService.container(newContainerId)?.let { replacement ->
            containerUpdateChecker.containerReplaced(oldContainerId, replacement)
        }
        monitorManager.replaceMonitoredItemId(oldContainerId, newContainerId)
        containersHistoryRepository.moveHistoryToContainerId(oldContainerId, newContainerId)
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
