package com.krillsson.sysapi.workload

import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.workload.PowerCommandExecutor.Admission
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
class PowerCommandJobs(
    executors: List<PowerCommandExecutor>,
    private val runner: PowerCommandRunner,
    private val clock: Clock
) {
    companion object {
        private val FINISHED_JOB_RETENTION: Duration = Duration.ofMinutes(30)
    }

    sealed interface StartResult {
        data class Started(val jobId: UUID, val platform: WorkloadPlatform, val id: String) : StartResult
        data class Rejected(val reason: String) : StartResult
    }

    private inner class Job(
        val jobId: UUID,
        val platform: WorkloadPlatform,
        val id: String,
        val command: PowerCommand,
        val startedAt: Instant
    ) : PowerCommandProgress {
        private val firstEvent = PowerCommandRunning(jobId, platform, id, startedAt, platformTaskId = null)

        val events: Sinks.Many<PowerCommandEvent> = Sinks.many().replay().all<PowerCommandEvent>()
            .apply { tryEmitNext(firstEvent) }

        @Volatile
        var lastEvent: PowerCommandEvent = firstEvent

        @Volatile
        var finishedAt: Instant? = null

        override fun running(platformTaskId: String) {
            emit(PowerCommandRunning(jobId, platform, id, clock.instant(), platformTaskId), finished = false)
        }

        override fun succeeded(platformTaskId: String?, exitStatus: String?) {
            emit(PowerCommandSucceeded(jobId, platform, id, clock.instant(), platformTaskId, exitStatus), finished = true)
        }

        override fun failed(reason: String, platformTaskId: String?, log: List<String>, canForceStop: Boolean) {
            emit(PowerCommandFailed(jobId, platform, id, clock.instant(), platformTaskId, reason, log, canForceStop), finished = true)
        }

        @Synchronized
        private fun emit(event: PowerCommandEvent, finished: Boolean) {
            when (finishedAt) {
                null -> {
                    lastEvent = event
                    if (finished) {
                        finishedAt = clock.instant()
                    }
                    events.tryEmitNext(event)
                    if (finished) {
                        events.tryEmitComplete()
                    }
                }

                else -> logger.warn("Ignoring {} for the finished power command job {}", event::class.java.simpleName, jobId)
            }
        }

        fun snapshot(): PowerCommandJob {
            val event = lastEvent
            return PowerCommandJob(
                jobId = jobId,
                platform = platform,
                id = id,
                command = command,
                state = when (event) {
                    is PowerCommandSucceeded -> PowerCommandJobState.SUCCEEDED
                    is PowerCommandFailed -> PowerCommandJobState.FAILED
                    else -> PowerCommandJobState.RUNNING
                },
                startedAt = startedAt,
                finishedAt = finishedAt,
                lastEvent = event
            )
        }
    }

    private val logger by logger()

    private val executors = executors.associateBy { it.platform }

    private val jobs = ConcurrentHashMap<UUID, Job>()

    @Synchronized
    fun start(platform: WorkloadPlatform, id: String, command: PowerCommand): StartResult {
        val admission = executors[platform]?.admit(id, command)
            ?: Admission.Refused("${platform.name.lowercase()} takes no power commands")
        return when (admission) {
            is Admission.Refused -> StartResult.Rejected(admission.reason)
            is Admission.Admitted -> when {
                isRunning(platform, id) -> StartResult.Rejected("A command for ${admission.name} is already running")
                else -> launch(executors.getValue(platform), id, command)
            }
        }
    }

    fun events(jobId: UUID): Flux<PowerCommandEvent> = jobs[jobId]?.events?.asFlux() ?: Flux.empty()

    fun find(jobId: UUID): PowerCommandJob? = jobs[jobId]?.snapshot()

    fun current(): PowerCommandJob? = jobs.values
        .filter { it.finishedAt == null }
        .maxByOrNull { it.startedAt }
        ?.snapshot()

    fun latestFor(platform: WorkloadPlatform, id: String): PowerCommandJob? = jobs.values
        .filter { it.platform == platform && it.id == id }
        .maxByOrNull { it.startedAt }
        ?.snapshot()

    fun isRunning(platform: WorkloadPlatform, id: String): Boolean =
        jobs.values.any { it.platform == platform && it.id == id && it.finishedAt == null }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    fun forgetFinishedJobs() {
        val forgetBefore = clock.instant().minus(FINISHED_JOB_RETENTION)
        jobs.values.removeIf { job -> job.finishedAt?.isBefore(forgetBefore) == true }
    }

    private fun launch(executor: PowerCommandExecutor, id: String, command: PowerCommand): StartResult {
        val job = Job(UUID.randomUUID(), executor.platform, id, command, clock.instant())
        jobs[job.jobId] = job
        runner.submit { run(executor, job) }
        return StartResult.Started(job.jobId, job.platform, id)
    }

    private fun run(executor: PowerCommandExecutor, job: Job) {
        try {
            executor.run(job.id, job.command, job)
        } catch (exception: Exception) {
            logger.warn("Power command {} on {} {} failed", job.command, job.platform, job.id, exception)
            job.failed(exception.message ?: exception::class.java.simpleName)
        }
    }
}
