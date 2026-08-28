package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateJobState
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateStepChanged
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContainerUpdateJobsTest {

    private val prepared = ContainerRecreateService.Preparation.Ready(
        containerId = "a",
        name = "nginx",
        imageRef = "nginx:1.25",
        composeProject = null,
        pull = null
    )

    private val recreateService = mockk<ContainerRecreateService>()
    private val jobs = ContainerUpdateJobs(recreateService)

    @Test
    fun `has nothing current or findable before anything is started`() {
        jobs.current() shouldBe null
        jobs.find(UUID.randomUUID()) shouldBe null
    }

    @Test
    fun `reports a rejected start without registering a job`() {
        // Given
        every { recreateService.prepare("a", true) } returns
            ContainerRecreateService.Preparation.Rejected("a is managed by Swarm")

        // When
        jobs.start("a", true)

        // Then
        jobs.current() shouldBe null
    }

    @Test
    fun `is queued until its recreate work actually calls back, then running, then succeeded`() {
        // Given
        every { recreateService.prepare("a", true) } returns prepared
        val readyToStep = CountDownLatch(1)
        val stepped = CountDownLatch(1)
        val readyToFinish = CountDownLatch(1)
        every { recreateService.recreate(prepared, any()) } answers {
            val listener = secondArg<ContainerRecreator.Listener>()
            readyToStep.await(5, TimeUnit.SECONDS)
            listener.onStep(ContainerUpdateStep.INSPECTING_CONTAINER)
            stepped.countDown()
            readyToFinish.await(5, TimeUnit.SECONDS)
            RecreateContainerResult.Success("new-a", null)
        }

        // When
        val started = jobs.start("a", true).shouldBeInstanceOf<ContainerUpdateJobs.StartResult.Started>()

        // Then: recreate() hasn't called back yet
        val queued = jobs.find(started.jobId).shouldNotBeNull()
        queued.jobId shouldBe started.jobId
        queued.containerId shouldBe "a"
        queued.state shouldBe DockerContainerUpdateJobState.QUEUED
        queued.lastEvent shouldBe null
        queued.finishedAt shouldBe null
        jobs.current() shouldBe queued

        // When it's allowed to report its first step
        readyToStep.countDown()
        stepped.await(5, TimeUnit.SECONDS)

        // Then
        val running = jobs.find(started.jobId).shouldNotBeNull()
        running.state shouldBe DockerContainerUpdateJobState.RUNNING
        running.lastEvent.shouldBeInstanceOf<DockerContainerUpdateStepChanged>()
        jobs.current() shouldBe running

        // When it's allowed to finish
        readyToFinish.countDown()
        awaitTerminal(started.jobId)

        // Then
        val finished = jobs.find(started.jobId).shouldNotBeNull()
        finished.state shouldBe DockerContainerUpdateJobState.SUCCEEDED
        finished.finishedAt.shouldNotBeNull()
        jobs.current() shouldBe null
    }

    @Test
    fun `is failed when recreate reports a failure`() {
        // Given
        every { recreateService.prepare("a", true) } returns prepared
        every { recreateService.recreate(prepared, any()) } returns
            RecreateContainerResult.Failed(ContainerUpdateStep.PULLING_IMAGE, "Pulling failed", false)

        // When
        val started = jobs.start("a", true).shouldBeInstanceOf<ContainerUpdateJobs.StartResult.Started>()
        awaitTerminal(started.jobId)

        // Then
        val finished = jobs.find(started.jobId).shouldNotBeNull()
        finished.state shouldBe DockerContainerUpdateJobState.FAILED
    }

    @Test
    fun `current favours the job actually running over one still queued behind it`() {
        // Given
        every { recreateService.prepare("a", true) } returns prepared
        val readyToFinish = CountDownLatch(1)
        every { recreateService.recreate(prepared, any()) } answers {
            readyToFinish.await(5, TimeUnit.SECONDS)
            RecreateContainerResult.Success("new-a", null)
        }
        val preparedB = prepared.copy(containerId = "b")
        every { recreateService.prepare("b", true) } returns preparedB
        every { recreateService.recreate(preparedB, any()) } returns RecreateContainerResult.Success("new-b", null)

        // When
        val startedA = jobs.start("a", true).shouldBeInstanceOf<ContainerUpdateJobs.StartResult.Started>()
        val startedB = jobs.start("b", true).shouldBeInstanceOf<ContainerUpdateJobs.StartResult.Started>()

        // Then: a occupies the single worker thread, b sits behind it
        jobs.current()?.jobId shouldBe startedA.jobId
        jobs.find(startedB.jobId)?.state shouldBe DockerContainerUpdateJobState.QUEUED

        // When a finishes and b gets its turn
        readyToFinish.countDown()
        awaitTerminal(startedA.jobId)
        awaitTerminal(startedB.jobId)

        // Then
        jobs.current() shouldBe null
    }

    @Test
    fun `an unknown id is findable as nothing rather than an error`() {
        jobs.find(UUID.randomUUID()) shouldBe null
    }

    private fun awaitTerminal(jobId: UUID) {
        jobs.events(jobId).blockLast(Duration.ofSeconds(5))
    }
}
