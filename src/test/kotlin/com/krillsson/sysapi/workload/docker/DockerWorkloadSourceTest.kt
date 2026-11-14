package com.krillsson.sysapi.workload.docker

import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.Status
import com.krillsson.sysapi.docker.container
import com.krillsson.sysapi.workload.RunState
import com.krillsson.sysapi.workload.WorkloadKind
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class DockerWorkloadSourceTest {

    private val containerService = mockk<ContainerService>()
    private val source = DockerWorkloadSource(containerService)

    @Test
    fun `lists every container by its name without the leading slash`() {
        // Given
        every { containerService.status } returns Status.Available
        every { containerService.containers() } returns listOf(
            container("abc", "web"),
            container("def", "db").copy(state = State.EXITED)
        )

        // When
        val reading = source.read()

        // Then
        reading.workloads.map { Triple(it.name, it.kind, it.runState) } shouldContainExactly listOf(
            Triple("web", WorkloadKind.CONTAINER, RunState.RUNNING),
            Triple("db", WorkloadKind.CONTAINER, RunState.STOPPED)
        )
        reading.availability.available shouldBe true
    }

    @Test
    fun `reports a disabled docker as disabled`() {
        // Given
        every { containerService.status } returns Status.Disabled
        every { containerService.containers() } returns emptyList()

        // When
        val reading = source.read()

        // Then
        reading.workloads.shouldBeEmpty()
        reading.availability.isDisabled shouldBe true
        reading.availability.reason shouldStartWith "The docker support is currently disabled"
    }

    @Test
    fun `says why docker is unavailable`() {
        // Given
        every { containerService.status } returns Status.Unavailable(IllegalStateException("No docker.sock"))
        every { containerService.containers() } returns emptyList()

        // When
        val reading = source.read()

        // Then
        reading.availability.available shouldBe false
        reading.availability.isDisabled shouldBe false
        reading.availability.reason shouldBe "No docker.sock Type: IllegalStateException"
    }
}
