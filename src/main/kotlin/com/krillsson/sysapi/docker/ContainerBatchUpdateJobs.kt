package com.krillsson.sysapi.docker

import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerSkipped
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateContainerStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateEvent
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateFinished
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateJob
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateJobState
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateStarted
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateFailed
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

@Service
class ContainerBatchUpdateJobs(
    private val containerUpdateJobs: ContainerUpdateJobs
) {
    companion object {
        private val FINISHED_JOB_RETENTION = Duration.ofMinutes(30)
    }

    sealed interface StartResult {
        data class Started(val batchJobId: UUID, val containerIds: List<String>) : StartResult
        data class Rejected(val reason: String) : StartResult
    }

    sealed interface AbortResult {
        data class Accepted(val batchJobId: UUID) : AbortResult
        data class Rejected(val reason: String) : AbortResult
    }

    private class Batch(val containerIds: List<String>) {
        val events: Sinks.Many<DockerContainerBatchUpdateEvent> = Sinks.many().replay().all()
        val startedAt: Instant = Instant.now()

        @Volatile
        var lastEvent: DockerContainerBatchUpdateEvent? = null

        @Volatile
        var aborted: Boolean = false

        @Volatile
        var finishedAt: Instant? = null
    }

    private val logger by logger()

    private val batches = ConcurrentHashMap<UUID, Batch>()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "container-batch-update").apply { isDaemon = true }
    }

    fun start(containerIds: List<String>, pullImage: Boolean): StartResult {
        if (containerIds.isEmpty()) {
            return StartResult.Rejected("No containers to update")
        }

        val batchJobId = UUID.randomUUID()
        // A repeated id would have the batch wait on the same container's job twice; the second
        // wait can race the first job's own bookkeeping of when it finished and be rejected as
        // already running even though it just succeeded.
        val batch = Batch(containerIds.distinct())
        batches[batchJobId] = batch
        executor.execute { run(batchJobId, batch, pullImage) }
        return StartResult.Started(batchJobId, batch.containerIds)
    }

    fun abort(batchJobId: UUID): AbortResult {
        val batch = batches[batchJobId] ?: return AbortResult.Rejected("No such batch update")
        if (batch.finishedAt != null) {
            return AbortResult.Rejected("Batch update has already finished")
        }
        batch.aborted = true
        return AbortResult.Accepted(batchJobId)
    }

    fun events(batchJobId: UUID): Flux<DockerContainerBatchUpdateEvent> {
        return batches[batchJobId]?.events?.asFlux() ?: Flux.empty()
    }

    // Batches run one at a time on a single-thread executor, in the order they were submitted, so
    // the earliest not-yet-finished batch is always the one currently executing, if any
    fun current(): DockerContainerBatchUpdateJob? {
        return batches.entries
            .filter { it.value.finishedAt == null }
            .minByOrNull { it.value.startedAt }
            ?.let { (batchJobId, batch) -> batch.toSnapshot(batchJobId) }
    }

    fun find(batchJobId: UUID): DockerContainerBatchUpdateJob? = batches[batchJobId]?.toSnapshot(batchJobId)

    private fun Batch.toSnapshot(batchJobId: UUID): DockerContainerBatchUpdateJob {
        val state = when {
            finishedAt == null && lastEvent == null -> DockerContainerBatchUpdateJobState.QUEUED
            finishedAt == null -> DockerContainerBatchUpdateJobState.RUNNING
            else -> DockerContainerBatchUpdateJobState.FINISHED
        }
        return DockerContainerBatchUpdateJob(
            batchJobId = batchJobId,
            containerIds = containerIds,
            state = state,
            startedAt = startedAt,
            finishedAt = finishedAt,
            lastEvent = lastEvent
        )
    }

    private fun run(batchJobId: UUID, batch: Batch, pullImage: Boolean) {
        batch.emit(DockerContainerBatchUpdateStarted(batchJobId, Instant.now(), batch.containerIds))

        for (containerId in batch.containerIds) {
            if (batch.aborted) {
                batch.emit(DockerContainerBatchUpdateContainerSkipped(batchJobId, Instant.now(), containerId))
                continue
            }
            updateOne(batchJobId, batch, containerId, pullImage)
        }

        batch.finishedAt = Instant.now()
        batch.emit(DockerContainerBatchUpdateFinished(batchJobId, Instant.now(), batch.aborted))
        batch.events.tryEmitComplete()
    }

    private fun updateOne(batchJobId: UUID, batch: Batch, containerId: String, pullImage: Boolean) {
        when (val startResult = containerUpdateJobs.start(containerId, pullImage)) {
            is ContainerUpdateJobs.StartResult.Rejected -> {
                batch.emit(
                    DockerContainerBatchUpdateContainerFinished(
                        batchJobId = batchJobId,
                        timestamp = Instant.now(),
                        containerId = containerId,
                        jobId = null,
                        succeeded = false,
                        reason = startResult.reason
                    )
                )
            }

            is ContainerUpdateJobs.StartResult.Started -> {
                batch.emit(
                    DockerContainerBatchUpdateContainerStarted(
                        batchJobId,
                        Instant.now(),
                        containerId,
                        startResult.jobId
                    )
                )

                val terminal = try {
                    containerUpdateJobs.events(startResult.jobId)
                        .filter { it is DockerContainerUpdateSucceeded || it is DockerContainerUpdateFailed }
                        .blockFirst()
                } catch (e: Exception) {
                    logger.error("Waiting for the update of container {} to finish failed unexpectedly", containerId, e)
                    null
                }

                batch.emit(
                    DockerContainerBatchUpdateContainerFinished(
                        batchJobId = batchJobId,
                        timestamp = Instant.now(),
                        containerId = containerId,
                        jobId = startResult.jobId,
                        succeeded = terminal is DockerContainerUpdateSucceeded,
                        reason = when (terminal) {
                            is DockerContainerUpdateFailed -> terminal.reason
                            null -> "The update did not report an outcome"
                            else -> null
                        }
                    )
                )
            }
        }
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    fun forgetFinishedBatches() {
        val forgetBefore = Instant.now().minus(FINISHED_JOB_RETENTION)
        batches.entries.removeIf { (_, batch) -> batch.finishedAt?.isBefore(forgetBefore) == true }
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }

    private fun Batch.emit(event: DockerContainerBatchUpdateEvent) {
        lastEvent = event
        events.tryEmitNext(event)
    }
}
