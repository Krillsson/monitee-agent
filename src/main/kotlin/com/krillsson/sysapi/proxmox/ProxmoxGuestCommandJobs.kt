package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandEvent
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandFailed
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJob
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJobState
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandRunning
import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandSucceeded
import com.krillsson.sysapi.proxmox.ProxmoxClient.Result
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.TaskScheduler
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
class ProxmoxGuestCommandJobs(
    configuration: YAMLConfigFile,
    private val client: ProxmoxClient,
    private val proxmoxService: ProxmoxService,
    private val taskScheduler: TaskScheduler,
    private val clock: Clock
) {
    companion object {
        const val FAILED_CHECKS_BEFORE_GIVING_UP = 3
        const val LOG_TAIL_LINES = 20
        val CHECK_INTERVAL: Duration = Duration.ofSeconds(2)
        private val SHUTDOWN_GRACE: Duration = Duration.ofSeconds(30)
        private val FINISHED_JOB_RETENTION: Duration = Duration.ofMinutes(30)
        private val SHUTDOWN_TIMEOUT_FAILURES = listOf("got timeout", "container did not stop")
    }

    sealed interface StartResult {
        data class Started(val jobId: UUID, val guestId: String, val upid: String) : StartResult
        data class Rejected(val reason: String) : StartResult
    }

    private class Job(
        val guest: ProxmoxGuest,
        val command: ProxmoxGuestCommand,
        val upid: String,
        val startedAt: Instant,
        val deadline: Instant,
        firstEvent: ProxmoxGuestCommandEvent
    ) {
        val events: Sinks.Many<ProxmoxGuestCommandEvent> = Sinks.many().replay().all<ProxmoxGuestCommandEvent>()
            .apply { tryEmitNext(firstEvent) }

        @Volatile
        var lastEvent: ProxmoxGuestCommandEvent = firstEvent

        @Volatile
        var finishedAt: Instant? = null

        var failedChecks = 0
    }

    private val logger by logger()

    private val config = configuration.proxmox

    private val jobs = ConcurrentHashMap<UUID, Job>()

    private val timeout: Duration = maxOf(
        Duration.ofSeconds(config.commandTimeoutSeconds),
        Duration.ofSeconds(config.shutdownTimeoutSeconds).plus(SHUTDOWN_GRACE)
    )

    @Synchronized
    fun start(guestId: String, command: ProxmoxGuestCommand): StartResult {
        val status = proxmoxService.status()
        if (status is ProxmoxService.Status.Unavailable) {
            return StartResult.Rejected(status.reason)
        }
        if (status is ProxmoxService.Status.Disabled) {
            return StartResult.Rejected("Proxmox is not enabled in configuration.yml")
        }
        val guest = proxmoxService.guest(guestId) ?: return StartResult.Rejected("There is no guest with id $guestId")
        if (jobs.values.any { it.guest.id == guestId && it.finishedAt == null }) {
            return StartResult.Rejected("A command for ${guest.name} is already running")
        }
        proxmoxService.commandRefusal(guest, command)?.let { return StartResult.Rejected(it) }

        val form = ProxmoxGuestCommands.formFor(command, config.shutdownTimeoutSeconds)
        return when (val result = client.post("${guest.apiPath()}/status/${command.action}", form, String::class.java)) {
            is Result.Ok -> {
                val jobId = UUID.randomUUID()
                val now = clock.instant()
                val running = ProxmoxGuestCommandRunning(jobId, guestId, now, result.data)
                val job = Job(guest, command, result.data, now, now.plus(timeout), running)
                jobs[jobId] = job
                scheduleCheck(jobId, job)
                StartResult.Started(jobId, guestId, job.upid)
            }

            is Result.Failure -> StartResult.Rejected(ProxmoxConverter.failureReason(result, config.host, config.port))
        }
    }

    fun events(jobId: UUID): Flux<ProxmoxGuestCommandEvent> = jobs[jobId]?.events?.asFlux() ?: Flux.empty()

    fun find(jobId: UUID): ProxmoxGuestCommandJob? = jobs[jobId]?.toSnapshot(jobId)

    fun current(): ProxmoxGuestCommandJob? = jobs.entries
        .filter { it.value.finishedAt == null }
        .maxByOrNull { it.value.startedAt }
        ?.let { (jobId, job) -> job.toSnapshot(jobId) }

    fun latestFor(guestId: String): ProxmoxGuestCommandJob? = jobs.entries
        .filter { it.value.guest.id == guestId }
        .maxByOrNull { it.value.startedAt }
        ?.let { (jobId, job) -> job.toSnapshot(jobId) }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    fun forgetFinishedJobs() {
        val forgetBefore = clock.instant().minus(FINISHED_JOB_RETENTION)
        jobs.entries.removeIf { (_, job) -> job.finishedAt?.isBefore(forgetBefore) == true }
    }

    private fun scheduleCheck(jobId: UUID, job: Job) {
        taskScheduler.schedule({ check(jobId, job) }, clock.instant().plus(CHECK_INTERVAL))
    }

    private fun check(jobId: UUID, job: Job) {
        val result = try {
            client.get("/nodes/${job.guest.node}/tasks/${job.upid}/status", ProxmoxApi.TaskStatus::class.java)
        } catch (exception: Exception) {
            Result.TransportFailure(exception)
        }
        when (result) {
            is Result.Ok -> {
                if (result.data.status == "stopped") {
                    return finish(jobId, job, result.data.exitStatus)
                }
                job.failedChecks = 0
            }

            is Result.Failure -> {
                job.failedChecks++
                if (job.failedChecks >= FAILED_CHECKS_BEFORE_GIVING_UP) {
                    return fail(jobId, job, "Lost track of the task: ${ProxmoxConverter.failureReason(result, config.host, config.port)}")
                }
            }
        }
        if (clock.instant() >= job.deadline) {
            fail(jobId, job, "Proxmox did not finish the task within ${timeout.seconds} seconds, it may still be running there")
        } else {
            scheduleCheck(jobId, job)
        }
    }

    private fun finish(jobId: UUID, job: Job, exitStatus: String?) {
        when {
            exitStatus == "OK" || exitStatus?.startsWith("WARNINGS") == true ->
                job.conclude(ProxmoxGuestCommandSucceeded(jobId, job.guest.id, clock.instant(), job.upid, exitStatus))

            else -> fail(jobId, job, exitStatus ?: "Proxmox reported no exit status for the task", taskLog(job))
        }
    }

    private fun fail(jobId: UUID, job: Job, reason: String, log: List<String> = emptyList()) {
        val canForceStop = job.command in setOf(ProxmoxGuestCommand.SHUTDOWN, ProxmoxGuestCommand.REBOOT) &&
            SHUTDOWN_TIMEOUT_FAILURES.any { reason.contains(it) }
        job.conclude(ProxmoxGuestCommandFailed(jobId, job.guest.id, clock.instant(), job.upid, reason, log, canForceStop))
    }

    private fun taskLog(job: Job): List<String> {
        val result = client.getList("/nodes/${job.guest.node}/tasks/${job.upid}/log", ProxmoxApi.TaskLogLine::class.java)
        return (result as? Result.Ok)?.data.orEmpty().mapNotNull { it.t }.takeLast(LOG_TAIL_LINES)
    }

    private fun Job.conclude(event: ProxmoxGuestCommandEvent) {
        try {
            proxmoxService.guestChanged(guest)
        } catch (exception: Exception) {
            logger.warn("Failed to refresh Proxmox after a command on guest {}", guest.id, exception)
        }
        lastEvent = event
        finishedAt = clock.instant()
        events.tryEmitNext(event)
        events.tryEmitComplete()
    }

    private fun Job.toSnapshot(jobId: UUID): ProxmoxGuestCommandJob {
        val event = lastEvent
        return ProxmoxGuestCommandJob(
            jobId = jobId,
            guestId = guest.id,
            command = command,
            upid = upid,
            state = when (event) {
                is ProxmoxGuestCommandSucceeded -> ProxmoxGuestCommandJobState.SUCCEEDED
                is ProxmoxGuestCommandFailed -> ProxmoxGuestCommandJobState.FAILED
                else -> ProxmoxGuestCommandJobState.RUNNING
            },
            startedAt = startedAt,
            finishedAt = finishedAt,
            lastEvent = event
        )
    }
}
