package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.config.ProxmoxConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandFailed
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJobState
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandRunning
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandSucceeded
import com.krillsson.sysapi.proxmox.ProxmoxClient.Result
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ProxmoxGuestCommandJobsTest {

    private val upid = "UPID:pve1:00001A2B:0003C4D5:6731F0A0:qmshutdown:100:monitee@pve!agent:"
    private val statusPath = "/nodes/pve1/tasks/$upid/status"

    private val client = mockk<ProxmoxClient>()
    private val proxmoxService = mockk<ProxmoxService>(relaxed = true)
    private val scheduled = ArrayDeque<Runnable>()
    private val taskScheduler = mockk<TaskScheduler> {
        every { schedule(any(), any<Instant>()) } answers {
            scheduled.addLast(firstArg())
            mockk(relaxed = true)
        }
    }
    private var now = Instant.parse("2026-11-11T10:00:00Z")
    private val clock = mockk<Clock> { every { instant() } answers { now } }
    private val running = guest(100)

    @BeforeEach
    fun setUp() {
        every { proxmoxService.status() } returns ProxmoxService.Status.Available
        every { proxmoxService.guest("100") } returns running
        every { proxmoxService.commandRefusal(any(), any()) } returns null
        every { client.post(any(), any(), String::class.java) } returns Result.Ok(upid)
        every { client.getList("/nodes/pve1/tasks/$upid/log", ProxmoxApi.TaskLogLine::class.java) } returns
            Result.Ok(fixtureList("task-log-timeout.json"))
    }

    private fun jobs(configuration: ProxmoxConfiguration = ProxmoxConfiguration(enabled = true, shutdownTimeoutSeconds = 60)) =
        ProxmoxGuestCommandJobs(mockk<YAMLConfigFile> { every { proxmox } returns configuration }, client, proxmoxService, taskScheduler, clock)

    private fun taskStatus(vararg fixtures: String) {
        every { client.get(statusPath, ProxmoxApi.TaskStatus::class.java) } returnsMany
            fixtures.map { Result.Ok(fixture<ProxmoxApi.TaskStatus>(it)) }
    }

    private fun runScheduledChecks() {
        while (scheduled.isNotEmpty()) {
            now = now.plus(ProxmoxGuestCommandJobs.CHECK_INTERVAL)
            scheduled.removeFirst().run()
        }
    }

    @Test
    fun `posts the command to the guest and reports it running`() {
        // Given
        val jobs = jobs()

        // When
        val started = jobs.start("100", ProxmoxGuestCommand.SHUTDOWN)
            .shouldBeInstanceOf<ProxmoxGuestCommandJobs.StartResult.Started>()

        // Then
        verify { client.post("/nodes/pve1/qemu/100/status/shutdown", mapOf("timeout" to "60", "forceStop" to "0"), String::class.java) }
        started.upid shouldBe upid
        val job = jobs.find(started.jobId).shouldNotBeNull()
        job.state shouldBe ProxmoxGuestCommandJobState.RUNNING
        job.lastEvent shouldBe ProxmoxGuestCommandRunning(started.jobId, "100", now, upid)
        jobs.current() shouldBe job
        jobs.latestFor("100") shouldBe job
    }

    @Test
    fun `follows the task until proxmox reports it stopped and then completes the stream`() {
        // Given
        val jobs = jobs()
        taskStatus("task-status-running.json", "task-status-running.json", "task-status-ok.json")
        val started = jobs.start("100", ProxmoxGuestCommand.SHUTDOWN) as ProxmoxGuestCommandJobs.StartResult.Started

        // When
        runScheduledChecks()

        // Then
        val events = jobs.events(started.jobId).collectList().block().shouldNotBeNull()
        events.map { it::class } shouldContainExactly listOf(ProxmoxGuestCommandRunning::class, ProxmoxGuestCommandSucceeded::class)
        (events.last() as ProxmoxGuestCommandSucceeded).exitStatus shouldBe "OK"
        val job = jobs.find(started.jobId).shouldNotBeNull()
        job.state shouldBe ProxmoxGuestCommandJobState.SUCCEEDED
        job.finishedAt shouldBe now
        jobs.current().shouldBeNull()
        verify { proxmoxService.guestChanged(running) }
    }

    @Test
    fun `offers a force stop when a shutdown timed out, with the end of the task log`() {
        // Given
        val jobs = jobs()
        taskStatus("task-status-timeout.json")
        val started = jobs.start("100", ProxmoxGuestCommand.SHUTDOWN) as ProxmoxGuestCommandJobs.StartResult.Started

        // When
        runScheduledChecks()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<ProxmoxGuestCommandFailed>()
        failed.reason shouldBe "VM quit/powerdown failed - got timeout"
        failed.canForceStop shouldBe true
        failed.log.last() shouldBe "TASK ERROR: VM quit/powerdown failed - got timeout"
    }

    @Test
    fun `does not offer a force stop when a start failed`() {
        // Given
        val jobs = jobs()
        every { proxmoxService.guest("100") } returns running.copy(status = ProxmoxGuestStatus.STOPPED)
        taskStatus("task-status-timeout.json")
        val started = jobs.start("100", ProxmoxGuestCommand.START) as ProxmoxGuestCommandJobs.StartResult.Started

        // When
        runScheduledChecks()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<ProxmoxGuestCommandFailed>()
        failed.canForceStop shouldBe false
    }

    @Test
    fun `gives up after three checks in a row cannot reach proxmox, but not after one`() {
        // Given
        val jobs = jobs()
        val unreachable = Result.TransportFailure(IOException("connection reset"))
        every { client.get(statusPath, ProxmoxApi.TaskStatus::class.java) } returnsMany listOf(
            unreachable,
            Result.Ok(fixture("task-status-running.json")),
            unreachable,
            unreachable,
            unreachable
        )
        val started = jobs.start("100", ProxmoxGuestCommand.SHUTDOWN) as ProxmoxGuestCommandJobs.StartResult.Started

        // When
        runScheduledChecks()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<ProxmoxGuestCommandFailed>()
        failed.reason shouldStartWith "Lost track of the task: Could not reach Proxmox"
        verify(exactly = 5) { client.get(statusPath, ProxmoxApi.TaskStatus::class.java) }
    }

    @Test
    fun `stops following a task that outlives the shutdown timeout plus a grace period`() {
        // Given
        val jobs = jobs(ProxmoxConfiguration(enabled = true, shutdownTimeoutSeconds = 100, commandTimeoutSeconds = 10))
        every { client.get(statusPath, ProxmoxApi.TaskStatus::class.java) } returns Result.Ok(fixture("task-status-running.json"))
        val started = jobs.start("100", ProxmoxGuestCommand.SHUTDOWN) as ProxmoxGuestCommandJobs.StartResult.Started
        val startedAt = now

        // When
        runScheduledChecks()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<ProxmoxGuestCommandFailed>()
        failed.reason shouldBe "Proxmox did not finish the task within 130 seconds, it may still be running there"
        Duration.between(startedAt, now) shouldBe Duration.ofSeconds(130)
    }

    @Test
    fun `refuses a second command for a guest while one is running`() {
        // Given
        val jobs = jobs()
        jobs.start("100", ProxmoxGuestCommand.SHUTDOWN)

        // When
        val second = jobs.start("100", ProxmoxGuestCommand.FORCE_STOP)

        // Then
        second shouldBe ProxmoxGuestCommandJobs.StartResult.Rejected("A command for guest-100 is already running")
    }

    @Test
    fun `refuses a command the guest does not accept without asking proxmox`() {
        // Given
        val jobs = jobs()
        every { proxmoxService.commandRefusal(running, ProxmoxGuestCommand.START) } returns "guest-100 is already running"

        // When
        val result = jobs.start("100", ProxmoxGuestCommand.START)

        // Then
        result shouldBe ProxmoxGuestCommandJobs.StartResult.Rejected("guest-100 is already running")
        verify(exactly = 0) { client.post(any(), any(), String::class.java) }
    }

    @Test
    fun `passes on why proxmox refused the command`() {
        // Given
        val jobs = jobs()
        every { client.post(any(), any(), String::class.java) } returns
            Result.Forbidden("Permission check failed (/vms/100, VM.PowerMgmt)")

        // When
        val result = jobs.start("100", ProxmoxGuestCommand.SHUTDOWN)

        // Then
        result shouldBe ProxmoxGuestCommandJobs.StartResult.Rejected("Proxmox denied access: Permission check failed (/vms/100, VM.PowerMgmt)")
        jobs.latestFor("100").shouldBeNull()
    }

    @Test
    fun `refuses commands while proxmox is unavailable or the guest is unknown`() {
        // Given
        val jobs = jobs()
        every { proxmoxService.guest("999") } returns null

        // When
        val unknownGuest = jobs.start("999", ProxmoxGuestCommand.START)
        every { proxmoxService.status() } returns ProxmoxService.Status.Unavailable("Could not reach Proxmox at pve:8006")
        val unavailable = jobs.start("100", ProxmoxGuestCommand.START)

        // Then
        unknownGuest shouldBe ProxmoxGuestCommandJobs.StartResult.Rejected("There is no guest with id 999")
        unavailable shouldBe ProxmoxGuestCommandJobs.StartResult.Rejected("Could not reach Proxmox at pve:8006")
    }

    @Test
    fun `forgets a finished job after 30 minutes`() {
        // Given
        val jobs = jobs()
        taskStatus("task-status-ok.json")
        val started = jobs.start("100", ProxmoxGuestCommand.SHUTDOWN) as ProxmoxGuestCommandJobs.StartResult.Started
        runScheduledChecks()

        // When
        now = now.plus(Duration.ofMinutes(29))
        jobs.forgetFinishedJobs()
        val keptAfter29 = jobs.find(started.jobId)
        now = now.plus(Duration.ofMinutes(2))
        jobs.forgetFinishedJobs()

        // Then
        keptAfter29.shouldNotBeNull()
        jobs.find(started.jobId).shouldBeNull()
    }
}
