package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.config.ProxmoxConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.proxmox.ProxmoxClient.Result
import com.krillsson.sysapi.workload.CapturingScheduler
import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommandEvent
import com.krillsson.sysapi.workload.PowerCommandFailed
import com.krillsson.sysapi.workload.PowerCommandJobState
import com.krillsson.sysapi.workload.PowerCommandJobs
import com.krillsson.sysapi.workload.PowerCommandRunning
import com.krillsson.sysapi.workload.PowerCommandSucceeded
import com.krillsson.sysapi.workload.TestClock
import com.krillsson.sysapi.workload.WorkloadPlatform
import com.krillsson.sysapi.workload.immediateRunner
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.time.Duration

class ProxmoxPowerCommandExecutorTest {

    private val upid = "UPID:pve1:00001A2B:0003C4D5:6731F0A0:qmshutdown:100:monitee@pve!agent:"
    private val statusPath = "/nodes/pve1/tasks/$upid/status"

    private val client = mockk<ProxmoxClient>()
    private val proxmoxService = mockk<ProxmoxService>(relaxed = true)
    private val clock = TestClock()
    private val scheduler = CapturingScheduler(clock, ProxmoxPowerCommandExecutor.CHECK_INTERVAL)
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

    private fun jobs(configuration: ProxmoxConfiguration = ProxmoxConfiguration(enabled = true, shutdownTimeoutSeconds = 60)): PowerCommandJobs {
        val executor = ProxmoxPowerCommandExecutor(
            mockk<YAMLConfigFile> { every { proxmox } returns configuration },
            client,
            proxmoxService,
            scheduler.taskScheduler,
            clock.clock
        )
        return PowerCommandJobs(listOf(executor), immediateRunner(), clock.clock)
    }

    private fun PowerCommandJobs.start(id: String, command: PowerCommand) = start(WorkloadPlatform.PROXMOX, id, command)

    private fun PowerCommandJobs.started(command: PowerCommand = PowerCommand.SHUTDOWN) =
        start("100", command).shouldBeInstanceOf<PowerCommandJobs.StartResult.Started>()

    private fun taskStatus(vararg fixtures: String) {
        every { client.get(statusPath, ProxmoxApi.TaskStatus::class.java) } returnsMany
            fixtures.map { Result.Ok(fixture<ProxmoxApi.TaskStatus>(it)) }
    }

    @Test
    fun `posts the command to the guest and reports it running with the task's upid`() {
        // Given
        val jobs = jobs()

        // When
        val started = jobs.started()

        // Then
        verify { client.post("/nodes/pve1/qemu/100/status/shutdown", mapOf("timeout" to "60", "forceStop" to "0"), String::class.java) }
        val job = jobs.find(started.jobId).shouldNotBeNull()
        job.state shouldBe PowerCommandJobState.RUNNING
        job.lastEvent shouldBe PowerCommandRunning(started.jobId, WorkloadPlatform.PROXMOX, "100", clock.now, upid)
        jobs.current() shouldBe job
        jobs.latestFor(WorkloadPlatform.PROXMOX, "100") shouldBe job
    }

    @Test
    fun `posts a pause as a proxmox suspend`() {
        // Given
        val jobs = jobs()

        // When
        jobs.started(PowerCommand.PAUSE)

        // Then
        verify { client.post("/nodes/pve1/qemu/100/status/suspend", emptyMap(), String::class.java) }
    }

    @Test
    fun `follows the task until proxmox reports it stopped and then completes the stream`() {
        // Given
        val jobs = jobs()
        taskStatus("task-status-running.json", "task-status-running.json", "task-status-ok.json")
        val started = jobs.started()

        // When
        scheduler.runAll()

        // Then
        val events = jobs.events(started.jobId).collectList().block().shouldNotBeNull()
        events.map { it::class } shouldContainExactly listOf(
            PowerCommandRunning::class,
            PowerCommandRunning::class,
            PowerCommandSucceeded::class
        )
        val succeeded = events.last() as PowerCommandSucceeded
        succeeded.exitStatus shouldBe "OK"
        succeeded.platformTaskId shouldBe upid
        val job = jobs.find(started.jobId).shouldNotBeNull()
        job.state shouldBe PowerCommandJobState.SUCCEEDED
        job.finishedAt shouldBe clock.now
        jobs.current().shouldBeNull()
        verify { proxmoxService.guestChanged(running) }
    }

    @ParameterizedTest
    @CsvSource(
        "task-status-ok.json, SUCCEEDED",
        "task-status-timeout.json, FAILED"
    )
    fun `refreshes the guest before reporting that the command finished`(fixture: String, state: PowerCommandJobState) {
        // Given
        val jobs = jobs()
        taskStatus(fixture)
        val started = jobs.started()
        val received = mutableListOf<PowerCommandEvent>()
        jobs.events(started.jobId).subscribe { received += it }
        var receivedAtRefresh: List<PowerCommandEvent>? = null
        var stateAtRefresh: PowerCommandJobState? = null
        every { proxmoxService.guestChanged(running) } answers {
            receivedAtRefresh = received.toList()
            stateAtRefresh = jobs.find(started.jobId)?.state
        }

        // When
        scheduler.runAll()

        // Then
        receivedAtRefresh.shouldNotBeNull().map { it::class } shouldContainExactly
            listOf(PowerCommandRunning::class, PowerCommandRunning::class)
        stateAtRefresh shouldBe PowerCommandJobState.RUNNING
        jobs.find(started.jobId).shouldNotBeNull().state shouldBe state
        received.size shouldBe 3
    }

    @Test
    fun `reports the command finished even when refreshing the guest fails`() {
        // Given
        val jobs = jobs()
        taskStatus("task-status-ok.json")
        every { proxmoxService.guestChanged(running) } throws IllegalStateException("poll failed")
        val started = jobs.started()

        // When
        scheduler.runAll()

        // Then
        val events = jobs.events(started.jobId).collectList().block().shouldNotBeNull()
        events.last().shouldBeInstanceOf<PowerCommandSucceeded>()
        jobs.find(started.jobId).shouldNotBeNull().state shouldBe PowerCommandJobState.SUCCEEDED
    }

    @Test
    fun `offers a force stop when a shutdown timed out, with the end of the task log`() {
        // Given
        val jobs = jobs()
        taskStatus("task-status-timeout.json")
        val started = jobs.started()

        // When
        scheduler.runAll()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<PowerCommandFailed>()
        failed.reason shouldBe "VM quit/powerdown failed - got timeout"
        failed.canForceStop shouldBe true
        failed.platformTaskId shouldBe upid
        failed.log.last() shouldBe "TASK ERROR: VM quit/powerdown failed - got timeout"
    }

    @Test
    fun `does not offer a force stop when a start failed`() {
        // Given
        val jobs = jobs()
        every { proxmoxService.guest("100") } returns running.copy(status = ProxmoxGuestStatus.STOPPED)
        taskStatus("task-status-timeout.json")
        val started = jobs.started(PowerCommand.START)

        // When
        scheduler.runAll()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<PowerCommandFailed>()
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
        val started = jobs.started()

        // When
        scheduler.runAll()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<PowerCommandFailed>()
        failed.reason shouldStartWith "Lost track of the task: Could not reach Proxmox"
        verify(exactly = 5) { client.get(statusPath, ProxmoxApi.TaskStatus::class.java) }
    }

    @Test
    fun `stops following a task that outlives the shutdown timeout plus a grace period`() {
        // Given
        val jobs = jobs(ProxmoxConfiguration(enabled = true, shutdownTimeoutSeconds = 100, commandTimeoutSeconds = 10))
        every { client.get(statusPath, ProxmoxApi.TaskStatus::class.java) } returns Result.Ok(fixture("task-status-running.json"))
        val started = jobs.started()
        val startedAt = clock.now

        // When
        scheduler.runAll()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<PowerCommandFailed>()
        failed.reason shouldBe "Proxmox did not finish the task within 130 seconds, it may still be running there"
        Duration.between(startedAt, clock.now) shouldBe Duration.ofSeconds(130)
    }

    @Test
    fun `refuses a second command for a guest while one is running`() {
        // Given
        val jobs = jobs()
        jobs.started()

        // When
        val second = jobs.start("100", PowerCommand.FORCE_STOP)

        // Then
        second shouldBe PowerCommandJobs.StartResult.Rejected("A command for guest-100 is already running")
    }

    @Test
    fun `refuses a command the guest does not accept without asking proxmox`() {
        // Given
        val jobs = jobs()
        every { proxmoxService.commandRefusal(running, PowerCommand.START) } returns "guest-100 is already running"

        // When
        val result = jobs.start("100", PowerCommand.START)

        // Then
        result shouldBe PowerCommandJobs.StartResult.Rejected("guest-100 is already running")
        verify(exactly = 0) { client.post(any(), any(), String::class.java) }
    }

    @Test
    fun `fails the job with why proxmox refused the command, without a task to follow`() {
        // Given
        val jobs = jobs()
        every { client.post(any(), any(), String::class.java) } returns
            Result.Forbidden("Permission check failed (/vms/100, VM.PowerMgmt)")

        // When
        val started = jobs.started()

        // Then
        val failed = jobs.find(started.jobId).shouldNotBeNull().lastEvent.shouldBeInstanceOf<PowerCommandFailed>()
        failed.reason shouldBe "Proxmox denied access: Permission check failed (/vms/100, VM.PowerMgmt)"
        failed.platformTaskId.shouldBeNull()
        scheduler.pending.size shouldBe 0
    }

    @Test
    fun `refuses commands while proxmox is unavailable or disabled, or the guest is unknown`() {
        // Given
        val jobs = jobs()
        every { proxmoxService.guest("999") } returns null

        // When
        val unknownGuest = jobs.start("999", PowerCommand.START)
        every { proxmoxService.status() } returns ProxmoxService.Status.Unavailable("Could not reach Proxmox at pve:8006")
        val unavailable = jobs.start("100", PowerCommand.START)
        every { proxmoxService.status() } returns ProxmoxService.Status.Disabled
        val disabled = jobs.start("100", PowerCommand.START)

        // Then
        unknownGuest shouldBe PowerCommandJobs.StartResult.Rejected("There is no guest with id 999")
        unavailable shouldBe PowerCommandJobs.StartResult.Rejected("Could not reach Proxmox at pve:8006")
        disabled shouldBe PowerCommandJobs.StartResult.Rejected("Proxmox is not enabled in configuration.yml")
    }
}
