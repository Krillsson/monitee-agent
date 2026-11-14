package com.krillsson.sysapi.workload

import com.krillsson.sysapi.workload.PowerCommandExecutor.Admission
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration

class PowerCommandJobsTest {

    private val clock = TestClock()
    private val submitted = ArrayDeque<Runnable>()
    private val runner = mockk<PowerCommandRunner> {
        every { submit(any()) } answers { submitted.addLast(firstArg()) }
    }
    private val docker = FakePowerCommandExecutor(WorkloadPlatform.DOCKER)
    private val unraid = FakePowerCommandExecutor(WorkloadPlatform.UNRAID)
    private val jobs = PowerCommandJobs(listOf(docker, unraid), runner, clock.clock)

    private fun started(platform: WorkloadPlatform = WorkloadPlatform.DOCKER, id: String = "abc", command: PowerCommand = PowerCommand.SHUTDOWN) =
        jobs.start(platform, id, command).shouldBeInstanceOf<PowerCommandJobs.StartResult.Started>()

    private fun runSubmitted() {
        while (submitted.isNotEmpty()) {
            submitted.removeFirst().run()
        }
    }

    @Test
    fun `answers at once and runs the command on its own threads, not the caller's`() {
        // When
        val started = started()

        // Then
        docker.runs.shouldBeEmpty()
        jobs.find(started.jobId).shouldNotBeNull().state shouldBe PowerCommandJobState.RUNNING
        runSubmitted()
        docker.runs shouldContainExactly listOf("abc" to PowerCommand.SHUTDOWN)
    }

    @Test
    fun `replays every event to a late subscriber and completes after the last`() {
        // Given
        val started = started()
        runSubmitted()
        val progress = docker.progress.shouldNotBeNull()

        // When
        progress.running("task-1")
        clock.advance(Duration.ofSeconds(5))
        progress.succeeded("task-1", "OK")

        // Then
        val events = jobs.events(started.jobId).collectList().block().shouldNotBeNull()
        events.map { it::class } shouldContainExactly listOf(
            PowerCommandRunning::class,
            PowerCommandRunning::class,
            PowerCommandSucceeded::class
        )
        (events.first() as PowerCommandRunning).platformTaskId.shouldBeNull()
        (events[1] as PowerCommandRunning).platformTaskId shouldBe "task-1"
        events.last() shouldBe PowerCommandSucceeded(started.jobId, WorkloadPlatform.DOCKER, "abc", clock.now, "task-1", "OK")
        val job = jobs.find(started.jobId).shouldNotBeNull()
        job.state shouldBe PowerCommandJobState.SUCCEEDED
        job.finishedAt shouldBe clock.now
        job.command shouldBe PowerCommand.SHUTDOWN
    }

    @Test
    fun `ignores what an executor reports after the job has finished`() {
        // Given
        val started = started()
        runSubmitted()
        val progress = docker.progress.shouldNotBeNull()
        progress.failed("timed out", canForceStop = true)

        // When
        progress.succeeded()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<PowerCommandFailed>()
        failed.reason shouldBe "timed out"
        failed.canForceStop shouldBe true
    }

    @Test
    fun `fails the job when the executor throws`() {
        // Given
        val throwing = FakePowerCommandExecutor(WorkloadPlatform.PROXMOX) { throw IllegalStateException("connection reset") }
        val jobs = PowerCommandJobs(listOf(throwing), immediateRunner(), clock.clock)

        // When
        val started = jobs.start(WorkloadPlatform.PROXMOX, "100", PowerCommand.START) as PowerCommandJobs.StartResult.Started

        // Then
        val job = jobs.find(started.jobId).shouldNotBeNull()
        job.state shouldBe PowerCommandJobState.FAILED
        (job.lastEvent as PowerCommandFailed).reason shouldBe "connection reset"
    }

    @Test
    fun `refuses a second command for the same id on the same platform while one is running`() {
        // Given
        started()

        // When
        val second = jobs.start(WorkloadPlatform.DOCKER, "abc", PowerCommand.FORCE_STOP)
        val otherPlatform = jobs.start(WorkloadPlatform.UNRAID, "abc", PowerCommand.FORCE_STOP)

        // Then
        second shouldBe PowerCommandJobs.StartResult.Rejected("A command for web is already running")
        otherPlatform.shouldBeInstanceOf<PowerCommandJobs.StartResult.Started>()
        jobs.isRunning(WorkloadPlatform.DOCKER, "abc") shouldBe true
    }

    @Test
    fun `passes on the executor's refusal without creating a job`() {
        // Given
        docker.admission = Admission.Refused("web is stopped and does not accept SHUTDOWN")

        // When
        val result = jobs.start(WorkloadPlatform.DOCKER, "abc", PowerCommand.SHUTDOWN)

        // Then
        result shouldBe PowerCommandJobs.StartResult.Rejected("web is stopped and does not accept SHUTDOWN")
        jobs.latestFor(WorkloadPlatform.DOCKER, "abc").shouldBeNull()
        submitted.shouldBeEmpty()
    }

    @Test
    fun `refuses a platform that has no executor`() {
        // When
        val result = jobs.start(WorkloadPlatform.PROXMOX, "100", PowerCommand.START)

        // Then
        result shouldBe PowerCommandJobs.StartResult.Rejected("proxmox takes no power commands")
    }

    @Test
    fun `answers the running job as current and the newest job per id`() {
        // Given
        val first = started(id = "abc")
        runSubmitted()
        docker.progress.shouldNotBeNull().succeeded()
        clock.advance(Duration.ofSeconds(1))
        val second = started(id = "abc", command = PowerCommand.START)
        clock.advance(Duration.ofSeconds(1))
        val other = started(platform = WorkloadPlatform.UNRAID, id = "def")

        // When
        val current = jobs.current()
        val latest = jobs.latestFor(WorkloadPlatform.DOCKER, "abc")

        // Then
        current.shouldNotBeNull().jobId shouldBe other.jobId
        latest.shouldNotBeNull().jobId shouldBe second.jobId
        jobs.find(first.jobId).shouldNotBeNull().state shouldBe PowerCommandJobState.SUCCEEDED
    }

    @Test
    fun `forgets a finished job after 30 minutes and keeps a running one`() {
        // Given
        val finished = started(id = "abc")
        runSubmitted()
        docker.progress.shouldNotBeNull().succeeded()
        val running = started(id = "def")

        // When
        clock.advance(Duration.ofMinutes(29))
        jobs.forgetFinishedJobs()
        val keptAfter29 = jobs.find(finished.jobId)
        clock.advance(Duration.ofMinutes(2))
        jobs.forgetFinishedJobs()

        // Then
        keptAfter29.shouldNotBeNull()
        jobs.find(finished.jobId).shouldBeNull()
        jobs.find(running.jobId).shouldNotBeNull()
    }
}
