package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.Config
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateEligibility
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.HostConfig
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.docker.updates.ContainerUpdateChecker
import com.krillsson.sysapi.docker.updates.RegistryClient
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

class ContainerRecreateServiceTest {

    private val replacedContainerId = "0ldc0nta1ner"
    private val replacementContainerId = "n3wc0nta1ner"
    private val replacedImageId = "sha256:0ld"

    private val containerService = mockk<ContainerService>(relaxed = true)
    private val dockerClient = mockk<DockerClient>(relaxed = true)
    private val selfContainer = mockk<SelfContainer>(relaxed = true)
    private val listener = mockk<ContainerRecreator.Listener>(relaxed = true)

    private val service = ContainerRecreateService(
        containerService = containerService,
        dockerClient = dockerClient,
        registryClient = mockk<RegistryClient>(relaxed = true),
        containerUpdateChecker = mockk<ContainerUpdateChecker>(relaxed = true),
        selfContainer = selfContainer,
        containersHistoryRepository = mockk<ContainersHistoryRepository>(relaxed = true),
        monitorManager = mockk<MonitorManager>(relaxed = true),
        eventManager = mockk<EventManager>(relaxed = true)
    )

    private val prepared = ContainerRecreateService.Preparation.Ready(
        containerId = replacedContainerId,
        name = "nginx",
        imageRef = "nginx:1.25",
        composeProject = null,
        pull = null
    )

    @Test
    fun `removes the replaced image after the container it belonged to`() {
        // Given
        recreateSucceeds()
        every { dockerClient.isImageOrphaned(replacedImageId) } returns true

        // When
        val result = service.recreate(prepared, listener)

        // Then
        result shouldBe RecreateContainerResult.Success(replacementContainerId, null)
        verifyOrder {
            dockerClient.removeContainer(replacedContainerId)
            listener.onStep(ContainerUpdateStep.REMOVING_REPLACED_IMAGE)
            dockerClient.removeImage(replacedImageId)
        }
    }

    @Test
    fun `leaves an image another container still uses alone`() {
        // Given
        recreateSucceeds()
        every { dockerClient.isImageOrphaned(replacedImageId) } returns false

        // When
        service.recreate(prepared, listener)

        // Then
        verify(exactly = 0) { dockerClient.removeImage(any()) }
        verify(exactly = 0) { listener.onStep(ContainerUpdateStep.REMOVING_REPLACED_IMAGE) }
    }

    @Test
    fun `does not look for an image to remove when the container did not report one`() {
        // Given
        recreateSucceeds(imageId = null)

        // When
        service.recreate(prepared, listener)

        // Then
        verify(exactly = 0) { dockerClient.isImageOrphaned(any()) }
        verify(exactly = 0) { dockerClient.removeImage(any()) }
    }

    @Test
    fun `removes nothing when the container could not be recreated`() {
        // Given
        every { dockerClient.recreateContainer(any(), any(), any()) } returns ContainerRecreator.Result.Failed(
            ContainerUpdateStep.CREATING_CONTAINER,
            "Recreating nginx failed",
            true
        )

        // When
        val result = service.recreate(prepared, listener)

        // Then
        result shouldBe RecreateContainerResult.Failed(
            ContainerUpdateStep.CREATING_CONTAINER,
            "Recreating nginx failed",
            true
        )
        verify(exactly = 0) { dockerClient.removeContainer(any()) }
        verify(exactly = 0) { dockerClient.isImageOrphaned(any()) }
        verify(exactly = 0) { dockerClient.removeImage(any()) }
    }

    @Test
    fun `updateEligibility allows a container nothing else depends on`() {
        // Given
        val gluetun = container(id = "gluet0n", name = "gluetun")

        // When
        val eligibility = service.updateEligibility(gluetun)

        // Then
        eligibility shouldBe ContainerUpdateEligibility(updatable = true, reason = null)
    }

    @Test
    fun `updateEligibility refuses the container monitee-agent itself runs in`() {
        // Given
        val self = container(id = "s3lf", name = "monitee-agent")
        every { selfContainer.isSelf("s3lf") } returns true

        // When
        val eligibility = service.updateEligibility(self)

        // Then
        eligibility shouldBe ContainerUpdateEligibility(
            updatable = false,
            reason = "monitee-agent is running monitee-agent itself, which cannot survive being replaced mid-update. Update it from the host instead"
        )
    }

    @Test
    fun `updateEligibility refuses a container managed by Swarm`() {
        // Given
        val swarmed = container(id = "sw4rm", name = "nginx", labels = mapOf("com.docker.swarm.service.id" to "abc"))

        // When
        val eligibility = service.updateEligibility(swarmed)

        // Then
        eligibility shouldBe ContainerUpdateEligibility(
            updatable = false,
            reason = "nginx is managed by Swarm, update its service instead"
        )
    }

    @Test
    fun `updateEligibility refuses a container whose network other containers depend on`() {
        // Given
        val gluetun = container(id = "gluet0n", name = "gluetun")
        val qbittorrent = container(id = "qb1t", name = "qbittorrent", networkMode = "container:gluet0n")
        every { containerService.containers() } returns listOf(gluetun, qbittorrent)

        // When
        val eligibility = service.updateEligibility(gluetun)

        // Then
        eligibility shouldBe ContainerUpdateEligibility(
            updatable = false,
            reason = "gluetun's network is used by qbittorrent, which cannot follow it to a new container"
        )
    }

    @Test
    fun `prepare refuses a container it is not eligible to update, with the same reason updateEligibility gives`() {
        // Given
        every { containerService.status } returns Status.Available
        every { containerService.container(replacedContainerId) } returns container(id = replacedContainerId, name = "monitee-agent")
        every { selfContainer.isSelf(replacedContainerId) } returns true

        // When
        val result = service.prepare(replacedContainerId, pullImage = true)

        // Then
        result shouldBe ContainerRecreateService.Preparation.Rejected(
            "monitee-agent is running monitee-agent itself, which cannot survive being replaced mid-update. Update it from the host instead"
        )
    }

    private fun recreateSucceeds(imageId: String? = replacedImageId) {
        every { containerService.container(any()) } returns null
        every { dockerClient.recreateContainer(any(), any(), any()) } returns
            ContainerRecreator.Result.Success(replacementContainerId, imageId)
    }

    private fun container(
        id: String,
        name: String,
        labels: Map<String, String> = emptyMap(),
        networkMode: String = "bridge",
        image: String = "nginx:1.25"
    ): Container = Container(
        command = "nginx",
        created = 0L,
        hostConfig = HostConfig(networkMode = networkMode),
        config = Config(env = emptyList(), volumeBindings = emptyList(), cmd = emptyList(), exposedPorts = emptyList()),
        id = id,
        image = image,
        imageID = "sha256:$id",
        labels = labels,
        mounts = emptyList(),
        names = listOf("/$name"),
        networkSettings = emptyList(),
        ports = emptyList(),
        sizeRootFs = 0L,
        sizeRw = 0L,
        state = State.RUNNING,
        health = null,
        status = "Up"
    )
}
