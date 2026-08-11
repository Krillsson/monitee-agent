package com.krillsson.sysapi.docker

import com.github.dockerjava.api.command.InspectImageCmd
import com.github.dockerjava.api.command.InspectImageResponse
import com.github.dockerjava.api.command.ListContainersCmd
import com.github.dockerjava.api.command.RemoveImageCmd
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Container
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import com.github.dockerjava.api.DockerClient as DockerJavaClient

class ContainerRecreatorTest {

    private val imageId = "sha256:0ld"

    private val client = mockk<DockerJavaClient>()
    private val recreator = ContainerRecreator(client)

    @Test
    fun `reports an untagged image that no container uses as orphaned`() {
        // Given
        imageHasTags()
        containersRunImages("sha256:new")

        // When
        val orphaned = recreator.isImageOrphaned(imageId)

        // Then
        orphaned shouldBe true
    }

    @Test
    fun `treats the placeholder repo tag as untagged`() {
        // Given
        imageHasTags("<none>:<none>")
        containersRunImages("sha256:new")

        // When
        val orphaned = recreator.isImageOrphaned(imageId)

        // Then
        orphaned shouldBe true
    }

    @Test
    fun `does not report an image a stopped container still uses as orphaned`() {
        // Given
        imageHasTags()
        containersRunImages("sha256:new", imageId)

        // When
        val orphaned = recreator.isImageOrphaned(imageId)

        // Then
        orphaned shouldBe false
    }

    @Test
    fun `does not report an image that kept a tag as orphaned`() {
        // Given
        imageHasTags("nginx:1.25")

        // When
        val orphaned = recreator.isImageOrphaned(imageId)

        // Then
        orphaned shouldBe false
        verify(exactly = 0) { client.listContainersCmd() }
    }

    @Test
    fun `does not report an image it cannot inspect as orphaned`() {
        // Given
        every { client.inspectImageCmd(imageId) } returns mockk {
            every { exec() } throws NotFoundException("no such image")
        }

        // When
        val orphaned = recreator.isImageOrphaned(imageId)

        // Then
        orphaned shouldBe false
    }

    @Test
    fun `does not report an image as orphaned when the containers cannot be listed`() {
        // Given
        imageHasTags()
        every { client.listContainersCmd() } returns mockk<ListContainersCmd> {
            every { withShowAll(any()) } returns this
            every { exec() } throws RuntimeException("daemon is gone")
        }

        // When
        val orphaned = recreator.isImageOrphaned(imageId)

        // Then
        orphaned shouldBe false
    }

    @Test
    fun `removes an image`() {
        // Given
        val command = mockk<RemoveImageCmd> { every { exec() } returns null }
        every { client.removeImageCmd(imageId) } returns command

        // When
        val removed = recreator.removeImage(imageId)

        // Then
        removed shouldBe true
        verify { command.exec() }
    }

    @Test
    fun `reports a removal the daemon refused instead of throwing`() {
        // Given
        every { client.removeImageCmd(imageId) } returns mockk {
            every { exec() } throws RuntimeException("image is being used")
        }

        // When
        val removed = recreator.removeImage(imageId)

        // Then
        removed shouldBe false
    }

    private fun imageHasTags(vararg tags: String) {
        val response = InspectImageResponse().withRepoTags(tags.toList())
        every { client.inspectImageCmd(imageId) } returns mockk<InspectImageCmd> {
            every { exec() } returns response
        }
    }

    private fun containersRunImages(vararg imageIds: String) {
        val containers = imageIds.map { image -> mockk<Container> { every { imageId } returns image } }
        every { client.listContainersCmd() } returns mockk<ListContainersCmd> {
            every { withShowAll(any()) } returns this
            every { exec() } returns containers
        }
    }
}
