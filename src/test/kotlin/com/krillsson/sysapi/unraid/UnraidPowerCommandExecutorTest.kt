package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.config.UnraidConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.unraid.UnraidClient.Result
import com.krillsson.sysapi.workload.CapturingScheduler
import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommandConfirmation
import com.krillsson.sysapi.workload.PowerCommandExecutor.Admission
import com.krillsson.sysapi.workload.PowerCommandJobs
import com.krillsson.sysapi.workload.PowerCommandRunner
import com.krillsson.sysapi.workload.RecordingProgress
import com.krillsson.sysapi.workload.RecordingProgress.Event
import com.krillsson.sysapi.workload.TestClock
import com.krillsson.sysapi.workload.WorkloadPlatform
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class UnraidPowerCommandExecutorTest {

    private val client = mockk<UnraidClient>()
    private val vmService = mockk<UnraidVmService>(relaxed = true)
    private val clock = TestClock()
    private val scheduler = CapturingScheduler(clock, PowerCommandConfirmation.CHECK_INTERVAL)
    private val progress = RecordingProgress()
    private val executor = UnraidPowerCommandExecutor(
        mockk<YAMLConfigFile> { every { unraid } returns UnraidConfiguration(enabled = true, apiKey = "key") },
        client,
        vmService,
        PowerCommandConfirmation(scheduler.taskScheduler, clock.clock)
    )

    @BeforeEach
    fun setUp() {
        every { vmService.status() } returns UnraidVmService.Status.Available
        every { vmService.snapshot() } returns unraidSnapshot(unraidVm())
        every { vmService.vm(WINDOWS_UUID) } returns unraidVm()
    }

    private fun vmStates(vararg states: UnraidVmState) {
        every { vmService.vm(WINDOWS_UUID) } returnsMany states.map { unraidVm(state = it) }
    }

    @Test
    fun `reports success only after it has polled unraid and seen the vm reach the new state`() {
        // Given
        every { client.vmCommand("stop", WINDOWS_UUID) } returns Result.Ok(true)
        var eventsAtPoll: List<Event>? = null
        every { vmService.poll() } answers { eventsAtPoll = progress.events.toList() }
        vmStates(UnraidVmState.SHUTOFF)

        // When
        executor.run(WINDOWS_UUID, PowerCommand.SHUTDOWN, progress)

        // Then
        eventsAtPoll.shouldNotBeNull().shouldBeEmpty()
        progress.events shouldContainExactly listOf(Event.Succeeded(null, null))
    }

    @Test
    fun `keeps checking every two seconds until the vm reaches the new state`() {
        // Given
        every { client.vmCommand("start", WINDOWS_UUID) } returns Result.Ok(true)
        vmStates(UnraidVmState.SHUTOFF, UnraidVmState.SHUTOFF, UnraidVmState.RUNNING)
        val startedAt = clock.now

        // When
        executor.run(WINDOWS_UUID, PowerCommand.START, progress)
        scheduler.runAll()

        // Then
        progress.events shouldContainExactly listOf(Event.Succeeded(null, null))
        Duration.between(startedAt, clock.now) shouldBe Duration.ofSeconds(4)
        verify(exactly = 3) { vmService.poll() }
    }

    @Test
    fun `fails when unraid reported success but the vm has not changed state within 30 seconds`() {
        // Given
        every { client.vmCommand("start", WINDOWS_UUID) } returns Result.Ok(true)
        every { vmService.vm(WINDOWS_UUID) } returns unraidVm(state = UnraidVmState.SHUTOFF)
        val startedAt = clock.now

        // When
        executor.run(WINDOWS_UUID, PowerCommand.START, progress)
        scheduler.runAll()

        // Then
        progress.events shouldContainExactly listOf(
            Event.Failed("Unraid reported success, but the VM is off", null, emptyList(), canForceStop = false)
        )
        Duration.between(startedAt, clock.now) shouldBe Duration.ofSeconds(30)
    }

    @Test
    fun `fails with unraid's error without its set state prefix, after polling again`() {
        // Given
        every { client.vmCommand("pause", WINDOWS_UUID) } returns
            Result.GraphQlErrors(listOf("Failed to set VM state: Cannot pause VM in state SHUTOFF"), listOf("INTERNAL_SERVER_ERROR"))

        // When
        executor.run(WINDOWS_UUID, PowerCommand.PAUSE, progress)

        // Then
        progress.events shouldContainExactly listOf(
            Event.Failed("Cannot pause VM in state SHUTOFF", null, emptyList(), canForceStop = false)
        )
        verify(exactly = 1) { vmService.poll() }
        scheduler.pending.shouldBeEmpty()
    }

    @Test
    fun `offers a force stop when unraid gave up shutting the vm down for a reboot`() {
        // Given
        every { client.vmCommand("reboot", WINDOWS_UUID) } returns Result.GraphQlErrors(
            listOf("Failed to reboot VM: Graceful shutdown failed, please force stop the VM and try again"),
            listOf("INTERNAL_SERVER_ERROR")
        )

        // When
        executor.run(WINDOWS_UUID, PowerCommand.REBOOT, progress)

        // Then
        progress.events shouldContainExactly listOf(
            Event.Failed(
                "Failed to reboot VM: Graceful shutdown failed, please force stop the VM and try again",
                null,
                emptyList(),
                canForceStop = true
            )
        )
    }

    @Test
    fun `fails when unraid answers that the command did not take`() {
        // Given
        every { client.vmCommand("resume", WINDOWS_UUID) } returns Result.Ok(false)

        // When
        executor.run(WINDOWS_UUID, PowerCommand.RESUME, progress)

        // Then
        progress.events shouldContainExactly listOf(
            Event.Failed("Unraid reported that the VM did not take the command", null, emptyList(), canForceStop = false)
        )
    }

    @Test
    fun `sends the blocking mutation from the power command threads, never from the scheduler`() {
        // Given
        val submitted = ArrayDeque<Runnable>()
        val runner = mockk<PowerCommandRunner> { every { submit(any()) } answers { submitted.addLast(firstArg()) } }
        val jobs = PowerCommandJobs(listOf(executor), runner, clock.clock)
        every { client.vmCommand("stop", WINDOWS_UUID) } returns Result.Ok(true)
        vmStates(UnraidVmState.RUNNING, UnraidVmState.RUNNING, UnraidVmState.SHUTOFF)

        // When
        jobs.start(WorkloadPlatform.UNRAID, WINDOWS_UUID, PowerCommand.SHUTDOWN)

        // Then
        verify(exactly = 0) { client.vmCommand(any(), any()) }
        submitted.size shouldBe 1
        submitted.removeFirst().run()
        scheduler.runAll()
        verify(exactly = 1) { client.vmCommand("stop", WINDOWS_UUID) }
        verify(exactly = 2) { vmService.poll() }
    }

    @Test
    fun `refuses while unraid is disabled, unavailable or has its vm manager stopped, and for an unknown vm`() {
        // Given
        every { vmService.vm("unknown") } returns null

        // When
        val unknown = executor.admit("unknown", PowerCommand.START)
        every { vmService.snapshot() } returns unraidSnapshot(vmsUnavailableReason = UnraidConverter.VMS_DISABLED_REASON)
        val vmsDisabled = executor.admit(WINDOWS_UUID, PowerCommand.SHUTDOWN)
        every { vmService.status() } returns UnraidVmService.Status.Unavailable("Could not reach Unraid at http://localhost:80")
        val unavailable = executor.admit(WINDOWS_UUID, PowerCommand.SHUTDOWN)
        every { vmService.status() } returns UnraidVmService.Status.Disabled
        val disabled = executor.admit(WINDOWS_UUID, PowerCommand.SHUTDOWN)

        // Then
        unknown shouldBe Admission.Refused("There is no VM with id unknown")
        vmsDisabled shouldBe Admission.Refused(UnraidConverter.VMS_DISABLED_REASON)
        unavailable shouldBe Admission.Refused("Could not reach Unraid at http://localhost:80")
        disabled shouldBe Admission.Refused("Unraid is not enabled in configuration.yml")
    }

    @Test
    fun `admits a command the vm takes in its state under the vm's name`() {
        // When
        val admitted = executor.admit(WINDOWS_UUID, PowerCommand.SHUTDOWN)
        val refused = executor.admit(WINDOWS_UUID, PowerCommand.START)

        // Then
        admitted shouldBe Admission.Admitted("Windows 11")
        refused shouldBe Admission.Refused("Windows 11 is running and does not accept START")
    }
}
