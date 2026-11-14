package com.krillsson.sysapi.workload

import io.mockk.every
import io.mockk.mockk
import org.springframework.scheduling.TaskScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant

class TestClock(var now: Instant = Instant.parse("2026-11-14T10:00:00Z")) {
    val clock: Clock = mockk { every { instant() } answers { now } }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

class CapturingScheduler(private val clock: TestClock, private val interval: Duration) {
    val pending = ArrayDeque<Runnable>()

    val taskScheduler: TaskScheduler = mockk {
        every { schedule(any(), any<Instant>()) } answers {
            pending.addLast(firstArg())
            mockk(relaxed = true)
        }
    }

    fun runAll() {
        while (pending.isNotEmpty()) {
            clock.advance(interval)
            pending.removeFirst().run()
        }
    }
}

fun immediateRunner(): PowerCommandRunner = mockk {
    every { submit(any()) } answers { firstArg<Runnable>().run() }
}

class RecordingProgress : PowerCommandProgress {
    sealed interface Event {
        data class Running(val platformTaskId: String) : Event
        data class Succeeded(val platformTaskId: String?, val exitStatus: String?) : Event
        data class Failed(val reason: String, val platformTaskId: String?, val log: List<String>, val canForceStop: Boolean) : Event
    }

    val events = mutableListOf<Event>()

    override fun running(platformTaskId: String) {
        events += Event.Running(platformTaskId)
    }

    override fun succeeded(platformTaskId: String?, exitStatus: String?) {
        events += Event.Succeeded(platformTaskId, exitStatus)
    }

    override fun failed(reason: String, platformTaskId: String?, log: List<String>, canForceStop: Boolean) {
        events += Event.Failed(reason, platformTaskId, log, canForceStop)
    }
}

class FakePowerCommandExecutor(
    override val platform: WorkloadPlatform,
    var admission: PowerCommandExecutor.Admission = PowerCommandExecutor.Admission.Admitted("web"),
    private val action: (PowerCommandProgress) -> Unit = {}
) : PowerCommandExecutor {
    val runs = mutableListOf<Pair<String, PowerCommand>>()
    var progress: PowerCommandProgress? = null

    override fun admit(id: String, command: PowerCommand): PowerCommandExecutor.Admission = admission

    override fun run(id: String, command: PowerCommand, progress: PowerCommandProgress) {
        runs += id to command
        this.progress = progress
        action(progress)
    }
}
