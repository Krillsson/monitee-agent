package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerSkipped
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateEvent
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateJobState
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateEvent
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateFailed
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateSucceeded
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ContainerBatchUpdateJobsTest {

    private val containerUpdateJobs = mockk<ContainerUpdateJobs>()
    private val batchUpdateJobs = ContainerBatchUpdateJobs(containerUpdateJobs)

    @Test
    fun `refuses to start a batch with no containers`() {
        // When
        val result = batchUpdateJobs.start(emptyList(), true)

        // Then
        result shouldBe ContainerBatchUpdateJobs.StartResult.Rejected("No containers to update")
        verify(exactly = 0) { containerUpdateJobs.start(any(), any()) }
    }

    @Test
    fun `drops repeated container ids so a container is never waited on twice`() {
        // Given
        jobSucceeds("a")

        // When
        val started = batchUpdateJobs.start(listOf("a", "a"), true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()
        batchUpdateJobs.events(started.batchJobId).collectList().block(Duration.ofSeconds(5))

        // Then
        started.containerIds shouldBe listOf("a")
        verify(exactly = 1) { containerUpdateJobs.start("a", true) }
    }

    @Test
    fun `updates every container in order, one at a time`() {
        // Given
        val jobIdA = jobSucceeds("a")
        val jobIdB = jobSucceeds("b")

        // When
        val events = runToCompletion(listOf("a", "b"))

        // Then
        events.map { it::class } shouldBe listOf(
            DockerContainerBatchUpdateStarted::class,
            DockerContainerBatchUpdateContainerStarted::class,
            DockerContainerBatchUpdateContainerFinished::class,
            DockerContainerBatchUpdateContainerStarted::class,
            DockerContainerBatchUpdateContainerFinished::class,
            DockerContainerBatchUpdateFinished::class
        )
        events.containerStartedFor("a").jobId shouldBe jobIdA
        events.containerFinishedFor("a").jobId shouldBe jobIdA
        events.containerFinishedFor("a").succeeded shouldBe true
        events.containerStartedFor("b").jobId shouldBe jobIdB
        events.containerFinishedFor("b").succeeded shouldBe true
        (events.last() as DockerContainerBatchUpdateFinished).aborted shouldBe false
    }

    @Test
    fun `continues with the next container when one fails`() {
        // Given
        jobFails("a", "Pulling failed")
        val jobIdB = jobSucceeds("b")

        // When
        val events = runToCompletion(listOf("a", "b"))

        // Then
        val aFinished = events.containerFinishedFor("a")
        aFinished.succeeded shouldBe false
        aFinished.reason shouldBe "Pulling failed"
        val bFinished = events.containerFinishedFor("b")
        bFinished.jobId shouldBe jobIdB
        bFinished.succeeded shouldBe true
    }

    @Test
    fun `reports a container whose own update was refused as finished, not skipped`() {
        // Given
        every { containerUpdateJobs.start("a", true) } returns
            ContainerUpdateJobs.StartResult.Rejected("a is managed by Swarm")
        jobSucceeds("b")

        // When
        val events = runToCompletion(listOf("a", "b"))

        // Then
        val aFinished = events.containerFinishedFor("a")
        aFinished.jobId shouldBe null
        aFinished.succeeded shouldBe false
        aFinished.reason shouldBe "a is managed by Swarm"
        events.none { it is DockerContainerBatchUpdateContainerSkipped } shouldBe true
    }

    @Test
    fun `skips the remaining containers once the batch is aborted`() {
        // Given
        val jobIdA = UUID.randomUUID()
        val containerAEvents = Sinks.many().multicast().onBackpressureBuffer<DockerContainerUpdateEvent>()
        every { containerUpdateJobs.start("a", true) } returns ContainerUpdateJobs.StartResult.Started(jobIdA, "a")
        every { containerUpdateJobs.events(jobIdA) } returns containerAEvents.asFlux()

        val queue = LinkedBlockingQueue<DockerContainerBatchUpdateEvent>()
        val started = batchUpdateJobs.start(listOf("a", "b", "c"), true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()
        batchUpdateJobs.events(started.batchJobId).subscribe { queue.put(it) }

        queue.takeAs<DockerContainerBatchUpdateStarted>()
        queue.takeAs<DockerContainerBatchUpdateContainerStarted>()

        // When
        val abortResult = batchUpdateJobs.abort(started.batchJobId)
        containerAEvents.tryEmitNext(DockerContainerUpdateSucceeded(jobIdA, "a", Instant.now(), "new-a", null))

        // Then
        queue.takeAs<DockerContainerBatchUpdateContainerFinished>().succeeded shouldBe true
        queue.takeAs<DockerContainerBatchUpdateContainerSkipped>().containerId shouldBe "b"
        queue.takeAs<DockerContainerBatchUpdateContainerSkipped>().containerId shouldBe "c"
        queue.takeAs<DockerContainerBatchUpdateFinished>().aborted shouldBe true

        abortResult shouldBe ContainerBatchUpdateJobs.AbortResult.Accepted(started.batchJobId)
        verify(exactly = 0) { containerUpdateJobs.start("b", any()) }
        verify(exactly = 0) { containerUpdateJobs.start("c", any()) }
    }

    @Test
    fun `refuses to abort a batch that does not exist`() {
        // When
        val result = batchUpdateJobs.abort(UUID.randomUUID())

        // Then
        result shouldBe ContainerBatchUpdateJobs.AbortResult.Rejected("No such batch update")
    }

    @Test
    fun `refuses to abort a batch that has already finished`() {
        // Given
        jobSucceeds("a")
        val started = batchUpdateJobs.start(listOf("a"), true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()
        batchUpdateJobs.events(started.batchJobId).collectList().block(Duration.ofSeconds(5))

        // When
        val result = batchUpdateJobs.abort(started.batchJobId)

        // Then
        result shouldBe ContainerBatchUpdateJobs.AbortResult.Rejected("Batch update has already finished")
    }

    @Test
    fun `has nothing current or findable before anything is started`() {
        batchUpdateJobs.current() shouldBe null
        batchUpdateJobs.find(UUID.randomUUID()) shouldBe null
    }

    @Test
    fun `current reports the batch while it is running, without needing its id`() {
        // Given
        val jobIdA = UUID.randomUUID()
        val containerAEvents = Sinks.many().multicast().onBackpressureBuffer<DockerContainerUpdateEvent>()
        every { containerUpdateJobs.start("a", true) } returns ContainerUpdateJobs.StartResult.Started(jobIdA, "a")
        every { containerUpdateJobs.events(jobIdA) } returns containerAEvents.asFlux()

        val queue = LinkedBlockingQueue<DockerContainerBatchUpdateEvent>()
        val started = batchUpdateJobs.start(listOf("a"), true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()
        batchUpdateJobs.events(started.batchJobId).subscribe { queue.put(it) }
        queue.takeAs<DockerContainerBatchUpdateStarted>()
        queue.takeAs<DockerContainerBatchUpdateContainerStarted>()

        // When
        val current = batchUpdateJobs.current()

        // Then
        current?.batchJobId shouldBe started.batchJobId
        current?.state shouldBe DockerContainerBatchUpdateJobState.RUNNING
        current?.lastEvent.shouldBeInstanceOf<DockerContainerBatchUpdateContainerStarted>()

        // let it finish so it doesn't linger into the next test
        containerAEvents.tryEmitNext(DockerContainerUpdateSucceeded(jobIdA, "a", Instant.now(), "new-a", null))
        queue.takeAs<DockerContainerBatchUpdateContainerFinished>()
        queue.takeAs<DockerContainerBatchUpdateFinished>()
    }

    @Test
    fun `find reports the outcome of a batch that already finished, for a client that missed it`() {
        // Given
        jobSucceeds("a")
        val started = batchUpdateJobs.start(listOf("a"), true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()

        // When
        batchUpdateJobs.events(started.batchJobId).collectList().block(Duration.ofSeconds(5))

        // Then
        val finished = batchUpdateJobs.find(started.batchJobId)
        finished?.state shouldBe DockerContainerBatchUpdateJobState.FINISHED
        finished?.lastEvent.shouldBeInstanceOf<DockerContainerBatchUpdateFinished>()
        batchUpdateJobs.current() shouldBe null
    }

    @Test
    fun `current favours the batch actually running over one still queued behind it`() {
        // Given
        val jobIdA = UUID.randomUUID()
        val containerAEvents = Sinks.many().multicast().onBackpressureBuffer<DockerContainerUpdateEvent>()
        every { containerUpdateJobs.start("a", true) } returns ContainerUpdateJobs.StartResult.Started(jobIdA, "a")
        every { containerUpdateJobs.events(jobIdA) } returns containerAEvents.asFlux()
        jobSucceeds("b")

        val queue = LinkedBlockingQueue<DockerContainerBatchUpdateEvent>()
        val startedFirst = batchUpdateJobs.start(listOf("a"), true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()
        batchUpdateJobs.events(startedFirst.batchJobId).subscribe { queue.put(it) }
        queue.takeAs<DockerContainerBatchUpdateStarted>()
        queue.takeAs<DockerContainerBatchUpdateContainerStarted>()

        // When: a second batch is started while the first is still occupying the worker thread
        val startedSecond = batchUpdateJobs.start(listOf("b"), true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()

        // Then
        batchUpdateJobs.current()?.batchJobId shouldBe startedFirst.batchJobId
        batchUpdateJobs.find(startedSecond.batchJobId)?.state shouldBe DockerContainerBatchUpdateJobState.QUEUED

        // let both finish
        containerAEvents.tryEmitNext(DockerContainerUpdateSucceeded(jobIdA, "a", Instant.now(), "new-a", null))
        batchUpdateJobs.events(startedFirst.batchJobId).collectList().block(Duration.ofSeconds(5))
        batchUpdateJobs.events(startedSecond.batchJobId).collectList().block(Duration.ofSeconds(5))
    }

    private fun runToCompletion(containerIds: List<String>): List<DockerContainerBatchUpdateEvent> {
        val started = batchUpdateJobs.start(containerIds, true)
            .shouldBeInstanceOf<ContainerBatchUpdateJobs.StartResult.Started>()
        return batchUpdateJobs.events(started.batchJobId).collectList().block(Duration.ofSeconds(5)).orEmpty()
    }

    private fun List<DockerContainerBatchUpdateEvent>.containerStartedFor(containerId: String) =
        filterIsInstance<DockerContainerBatchUpdateContainerStarted>().single { it.containerId == containerId }

    private fun List<DockerContainerBatchUpdateEvent>.containerFinishedFor(containerId: String) =
        filterIsInstance<DockerContainerBatchUpdateContainerFinished>().single { it.containerId == containerId }

    private fun jobSucceeds(containerId: String): UUID {
        val jobId = UUID.randomUUID()
        every { containerUpdateJobs.start(containerId, true) } returns
            ContainerUpdateJobs.StartResult.Started(jobId, containerId)
        every { containerUpdateJobs.events(jobId) } returns Flux.just(
            DockerContainerUpdateSucceeded(jobId, containerId, Instant.now(), "new-$containerId", null)
        )
        return jobId
    }

    private fun jobFails(containerId: String, reason: String): UUID {
        val jobId = UUID.randomUUID()
        every { containerUpdateJobs.start(containerId, true) } returns
            ContainerUpdateJobs.StartResult.Started(jobId, containerId)
        every { containerUpdateJobs.events(jobId) } returns Flux.just(
            DockerContainerUpdateFailed(
                jobId,
                containerId,
                Instant.now(),
                ContainerUpdateStep.PULLING_IMAGE,
                reason,
                true
            )
        )
        return jobId
    }

    private inline fun <reified T> LinkedBlockingQueue<DockerContainerBatchUpdateEvent>.takeAs(): T {
        val event = poll(2, TimeUnit.SECONDS)
        return event as T
    }
}
