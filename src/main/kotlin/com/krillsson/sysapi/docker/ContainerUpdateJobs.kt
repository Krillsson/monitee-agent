package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.ImagePullLayer
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateEvent
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateFailed
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateImagePullProgress
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateStepChanged
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateSucceeded
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs container updates in the background and publishes what they are doing.
 *
 * Updates run one at a time on a single thread: a pull can hold the daemon busy for minutes and
 * two containers being replaced at once is not worth the trouble. A queued update reports nothing
 * until it starts.
 *
 * A finished job is kept around for a while so a subscriber that arrives late still sees how it
 * ended, then forgotten along with everything it emitted.
 */
@Service
class ContainerUpdateJobs(
    private val containerRecreateService: ContainerRecreateService
) {
    companion object {
        private val FINISHED_JOB_RETENTION = Duration.ofMinutes(30)
    }

    sealed interface StartResult {
        data class Started(val jobId: UUID, val containerId: String) : StartResult
        data class Rejected(val reason: String) : StartResult
    }

    private class Job(val containerId: String) {
        val events: Sinks.Many<DockerContainerUpdateEvent> = Sinks.many().replay().all()

        @Volatile
        var finishedAt: Instant? = null
    }

    private val logger by logger()

    private val jobs = ConcurrentHashMap<UUID, Job>()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "container-update").apply { isDaemon = true }
    }

    fun start(containerId: String, pullImage: Boolean): StartResult {
        if (jobs.values.any { it.containerId == containerId && it.finishedAt == null }) {
            return StartResult.Rejected("An update of this container is already running")
        }

        return when (val preparation = containerRecreateService.prepare(containerId, pullImage)) {
            is ContainerRecreateService.Preparation.Rejected -> StartResult.Rejected(preparation.reason)
            ContainerRecreateService.Preparation.Unavailable -> StartResult.Rejected("Docker client is unavailable")
            is ContainerRecreateService.Preparation.Ready -> {
                val jobId = UUID.randomUUID()
                val job = Job(containerId)
                jobs[jobId] = job
                executor.execute { run(jobId, job, preparation) }
                StartResult.Started(jobId, containerId)
            }
        }
    }

    /**
     * Everything the job has emitted so far followed by the rest of it, completing once it is done.
     * An unknown job — never started, or finished long enough ago to be forgotten — is an empty
     * stream rather than an error, since the update itself is unaffected either way.
     */
    fun events(jobId: UUID): Flux<DockerContainerUpdateEvent> {
        return jobs[jobId]?.events?.asFlux() ?: Flux.empty()
    }

    private fun run(jobId: UUID, job: Job, prepared: ContainerRecreateService.Preparation.Ready) {
        val listener = JobListener(jobId, job, prepared.imageRef)
        val result = try {
            containerRecreateService.recreate(prepared, listener)
        } catch (e: Exception) {
            logger.error("Updating container ${prepared.name} failed unexpectedly", e)
            RecreateContainerResult.Failed(listener.step, e.message ?: e::class.java.simpleName, false)
        }

        job.emit(
            when (result) {
                is RecreateContainerResult.Success -> DockerContainerUpdateSucceeded(
                    jobId = jobId,
                    containerId = job.containerId,
                    timestamp = Instant.now(),
                    newContainerId = result.containerId,
                    composeProject = result.composeProject
                )

                is RecreateContainerResult.Failed -> DockerContainerUpdateFailed(
                    jobId = jobId,
                    containerId = job.containerId,
                    timestamp = Instant.now(),
                    step = result.step,
                    reason = result.reason,
                    rolledBack = result.rolledBack
                )
            }
        )
        job.finishedAt = Instant.now()
        job.events.tryEmitComplete()
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    fun forgetFinishedJobs() {
        val forgetBefore = Instant.now().minus(FINISHED_JOB_RETENTION)
        jobs.entries.removeIf { (_, job) -> job.finishedAt?.isBefore(forgetBefore) == true }
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }

    private fun Job.emit(event: DockerContainerUpdateEvent) {
        events.tryEmitNext(event)
    }

    private inner class JobListener(
        private val jobId: UUID,
        private val job: Job,
        private val imageRef: String
    ) : DockerClient.RecreateListener {

        @Volatile
        var step: ContainerUpdateStep = ContainerUpdateStep.INSPECTING_CONTAINER
            private set

        override fun onStep(step: ContainerUpdateStep) {
            this.step = step
            job.emit(DockerContainerUpdateStepChanged(jobId, job.containerId, Instant.now(), step))
        }

        override fun onPullProgress(layers: List<ImagePullLayer>) {
            job.emit(
                DockerContainerUpdateImagePullProgress(
                    jobId = jobId,
                    containerId = job.containerId,
                    timestamp = Instant.now(),
                    imageRef = imageRef,
                    downloadedBytes = layers.mapNotNull { it.currentBytes }.sumOrNull(),
                    totalBytes = layers.mapNotNull { it.totalBytes }.sumOrNull(),
                    layers = layers
                )
            )
        }

        private fun List<Long>.sumOrNull(): Long? = if (isEmpty()) null else sum()
    }
}
