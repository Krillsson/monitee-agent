package com.krillsson.sysapi.graphql.domain

import java.time.Instant
import java.util.UUID

enum class DockerContainerBatchUpdateJobState {
    QUEUED,
    RUNNING,
    FINISHED
}

data class DockerContainerBatchUpdateJob(
    val batchJobId: UUID,
    val containerIds: List<String>,
    val state: DockerContainerBatchUpdateJobState,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val lastEvent: DockerContainerBatchUpdateEvent?
)
