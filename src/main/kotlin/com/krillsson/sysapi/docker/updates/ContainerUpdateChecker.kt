package com.krillsson.sysapi.docker.updates

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerImageUpdate
import com.krillsson.sysapi.core.domain.docker.ImageUpdateStatus
import com.krillsson.sysapi.core.genericevents.ContainerImageUpdateAvailable
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.DockerClient
import com.krillsson.sysapi.docker.Status
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.notifications.NotificationManager
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Compares the digest of every container's image with the one its registry currently serves.
 *
 * A container is only up for a new check once its interval has passed, so the sweep picks
 * up containers that were just created while the registry itself is queried at the
 * configured pace. Intervals shorter than the sweep are rounded up to it. Images that
 * cannot be compared — local builds, images pinned to a digest — are reported as skipped
 * instead of silently counting as up to date.
 */
@Service
class ContainerUpdateChecker(
    private val containerService: ContainerService,
    private val dockerClient: DockerClient,
    private val registryClient: RegistryClient,
    private val genericEventRepository: GenericEventRepository,
    private val notificationManager: NotificationManager,
    yamlConfigFile: YAMLConfigFile
) {
    companion object {
        private val RETRY_INTERVAL_AFTER_ERROR = Duration.ofMinutes(5)
    }

    private val logger by logger()

    private val config = yamlConfigFile.docker.updateCheck
    private val interval: Duration = Duration.ofMinutes(config.intervalMinutes)
    private val excludedContainers = config.excludeContainers.map { it.asPattern() }

    private val updates = ConcurrentHashMap<String, ContainerImageUpdate>()
    private val nextCheck = ConcurrentHashMap<String, Instant>()

    val enabled: Boolean = config.enabled

    fun updateForContainer(containerId: String): ContainerImageUpdate? = updates[containerId]

    fun updatesForContainers(containerIds: Collection<String>): Map<String, ContainerImageUpdate> =
        containerIds.mapNotNull { id -> updates[id]?.let { id to it } }.toMap()

    fun outdatedContainerIds(): Set<String> = updates.values
        .filter { it.status == ImageUpdateStatus.OUTDATED }
        .map { it.containerId }
        .toSet()

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (!config.enabled || containerService.status != Status.Available) {
            return
        }

        val containers = containerService.containers()
        forgetContainersThatAreGone(containers)

        val now = Instant.now()
        val due = containers.filter { nextCheck[it.id]?.isAfter(now) != true }
        if (due.isEmpty()) {
            return
        }

        logger.debug("Checking {} of {} containers for image updates", due.size, containers.size)
        val digests = mutableMapOf<ImageReference, RegistryClient.DigestResult>()
        due.forEach { container ->
            val update = check(container, digests)
            updates[container.id] = update
            nextCheck[container.id] = Instant.now().plus(
                if (update.status == ImageUpdateStatus.ERROR) RETRY_INTERVAL_AFTER_ERROR else interval
            )
            reflectInEvents(container, update)
        }
    }

    private fun check(
        container: Container,
        digests: MutableMap<ImageReference, RegistryClient.DigestResult>
    ): ContainerImageUpdate {
        val result = { status: ImageUpdateStatus, remoteDigest: String?, reason: String? ->
            ContainerImageUpdate(container.id, container.image, status, remoteDigest, reason, Instant.now())
        }

        if (excludedContainers.any { pattern -> container.names.any { pattern.matches(it.removePrefix("/")) } }) {
            return result(ImageUpdateStatus.SKIPPED, null, "Excluded by configuration")
        }

        val reference = ImageReference.parse(container.image)
            ?: return result(ImageUpdateStatus.SKIPPED, null, "${container.image} is not a registry image reference")
        if (reference.isPinnedToDigest) {
            return result(ImageUpdateStatus.SKIPPED, null, "Image is pinned to a digest")
        }
        if (!container.imageID.contains("sha256")) {
            return result(ImageUpdateStatus.SKIPPED, null, "Container does not reference an image by id")
        }

        val inspection = dockerClient.inspectImage(container.imageID)
            ?: return result(ImageUpdateStatus.ERROR, null, "Image ${container.imageID} could not be inspected")
        val localDigests = inspection.repoDigests.mapNotNull { digest ->
            digest.substringAfter('@', "").takeIf { it.isNotEmpty() }
        }
        if (localDigests.isEmpty()) {
            return result(ImageUpdateStatus.SKIPPED, null, "Image was not pulled from a registry")
        }

        return when (val remote = digests.getOrPut(reference) { registryClient.remoteDigest(reference) }) {
            is RegistryClient.DigestResult.Found -> if (localDigests.contains(remote.digest)) {
                result(ImageUpdateStatus.UP_TO_DATE, remote.digest, null)
            } else {
                result(ImageUpdateStatus.OUTDATED, remote.digest, null)
            }

            is RegistryClient.DigestResult.NotComparable -> result(ImageUpdateStatus.SKIPPED, null, remote.reason)
            is RegistryClient.DigestResult.Failed -> result(ImageUpdateStatus.ERROR, null, remote.reason)
        }
    }

    private fun reflectInEvents(container: Container, update: ContainerImageUpdate) {
        val existing = eventsForContainer(container.id)
        val remoteDigest = update.remoteDigest
        if (update.status != ImageUpdateStatus.OUTDATED || remoteDigest == null) {
            existing.forEach { genericEventRepository.removeById(it.id) }
            return
        }

        if (existing.any { it.remoteDigest == remoteDigest }) {
            return
        }

        existing.forEach { genericEventRepository.removeById(it.id) }
        val containerName = container.names.firstOrNull()?.removePrefix("/") ?: container.id
        logger.info("Creating event: {} can be updated to {}", containerName, remoteDigest)
        val event = ContainerImageUpdateAvailable(
            id = UUID.randomUUID(),
            timestamp = Instant.now(),
            containerId = container.id,
            containerName = containerName,
            imageRef = update.imageRef,
            remoteDigest = remoteDigest
        )
        genericEventRepository.add(event)
        if (config.notify) {
            notificationManager.notify(event.asNotification())
        }
    }

    private fun forgetContainersThatAreGone(containers: List<Container>) {
        val present = containers.map { it.id }.toSet()
        val gone = updates.keys.filterNot { present.contains(it) }
        gone.forEach { containerId ->
            updates.remove(containerId)
            nextCheck.remove(containerId)
            eventsForContainer(containerId).forEach { genericEventRepository.removeById(it.id) }
        }
    }

    private fun eventsForContainer(containerId: String) = genericEventRepository.read()
        .filterIsInstance<ContainerImageUpdateAvailable>()
        .filter { it.containerId == containerId }

    private fun String.asPattern(): Regex {
        val expression = split("*").joinToString(".*") { Regex.escape(it) }
        return Regex(expression, RegexOption.IGNORE_CASE)
    }
}

private fun ContainerImageUpdateAvailable.asNotification(): Notification {
    return Notification.GenericEvent.ContainerImageUpdateAvailable(
        id = id,
        timestamp = timestamp,
        containerId = containerId,
        containerName = containerName,
        imageRef = imageRef
    )
}
