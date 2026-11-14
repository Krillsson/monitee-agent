package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.Command
import com.krillsson.sysapi.core.domain.docker.CommandType
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.workload.CapturingScheduler
import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommandConfirmation
import com.krillsson.sysapi.workload.PowerCommandExecutor.Admission
import com.krillsson.sysapi.workload.RecordingProgress
import com.krillsson.sysapi.workload.RecordingProgress.Event
import com.krillsson.sysapi.workload.TestClock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class DockerPowerCommandExecutorTest {

    private val containerService = mockk<ContainerService>(relaxed = true)
    private val clock = TestClock()
    private val scheduler = CapturingScheduler(clock, PowerCommandConfirmation.CHECK_INTERVAL)
    private val progress = RecordingProgress()
    private val executor = DockerPowerCommandExecutor(containerService, PowerCommandConfirmation(scheduler.taskScheduler, clock.clock))
    private val web = container("abc", "web")

    @BeforeEach
    fun setUp() {
        every { containerService.status } returns Status.Available
        every { containerService.container("abc") } returns web
    }

    private fun states(vararg states: State) {
        every { containerService.container("abc") } returnsMany states.map { web.copy(state = it) }
    }

    @Test
    fun `kills the container for a force stop and reports success once it has exited`() {
        // Given
        every { containerService.killContainer("abc") } returns CommandResult.Success
        states(State.RUNNING, State.EXITED)

        // When
        executor.run("abc", PowerCommand.FORCE_STOP, progress)
        scheduler.runAll()

        // Then
        progress.events shouldContainExactly listOf(Event.Succeeded(null, null))
        verify(exactly = 0) { containerService.performCommandWithContainer(any()) }
        verifyOrder {
            containerService.killContainer("abc")
            containerService.invalidateContainersCache()
            containerService.container("abc")
        }
    }

    @Test
    fun `fails when docker reported success but the container is not running within ten seconds`() {
        // Given
        every { containerService.performCommandWithContainer(Command("abc", CommandType.START)) } returns CommandResult.Success
        every { containerService.container("abc") } returns web.copy(state = State.EXITED)
        val startedAt = clock.now

        // When
        executor.run("abc", PowerCommand.START, progress)
        scheduler.runAll()

        // Then
        progress.events shouldContainExactly listOf(
            Event.Failed("Docker reported success, but the container is stopped", null, emptyList(), canForceStop = false)
        )
        Duration.between(startedAt, clock.now) shouldBe Duration.ofSeconds(10)
    }

    @Test
    fun `fails with docker's error`() {
        // Given
        every { containerService.performCommandWithContainer(Command("abc", CommandType.PAUSE)) } returns
            CommandResult.Failed(IllegalStateException("Status 409: Container abc is not running"))

        // When
        executor.run("abc", PowerCommand.PAUSE, progress)

        // Then
        progress.events shouldContainExactly listOf(
            Event.Failed("Status 409: Container abc is not running", null, emptyList(), canForceStop = false)
        )
    }

    @Test
    fun `refuses while docker is unavailable, for an unknown container and for a state that does not take the command`() {
        // Given
        every { containerService.container("gone") } returns null

        // When
        val unknown = executor.admit("gone", PowerCommand.START)
        val refused = executor.admit("abc", PowerCommand.START)
        val admitted = executor.admit("abc", PowerCommand.SHUTDOWN)
        every { containerService.status } returns Status.Disabled
        val disabled = executor.admit("abc", PowerCommand.SHUTDOWN)

        // Then
        unknown shouldBe Admission.Refused("There is no container with id gone")
        refused shouldBe Admission.Refused("web is running and does not accept START")
        admitted shouldBe Admission.Admitted("web")
        disabled shouldBe Admission.Refused("The docker support is currently disabled. You can change this in configuration.yml")
    }
}
