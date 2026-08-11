package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
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
    private val listener = mockk<ContainerRecreator.Listener>(relaxed = true)

    private val service = ContainerRecreateService(
        containerService = containerService,
        dockerClient = dockerClient,
        registryClient = mockk<RegistryClient>(relaxed = true),
        containerUpdateChecker = mockk<ContainerUpdateChecker>(relaxed = true),
        selfContainer = mockk<SelfContainer>(relaxed = true),
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

    private fun recreateSucceeds(imageId: String? = replacedImageId) {
        every { containerService.container(any()) } returns null
        every { dockerClient.recreateContainer(any(), any(), any()) } returns
            ContainerRecreator.Result.Success(replacementContainerId, imageId)
    }
}
