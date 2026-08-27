package com.krillsson.sysapi.graphql.domain

import java.time.Instant
import java.util.UUID

interface DockerContainerBatchUpdateEvent {
    val batchJobId: UUID
    val timestamp: Instant
}

data class DockerContainerBatchUpdateStarted(
    override val batchJobId: UUID,
    override val timestamp: Instant,
    val containerIds: List<String>
) : DockerContainerBatchUpdateEvent

data class DockerContainerBatchUpdateContainerStarted(
    override val batchJobId: UUID,
    override val timestamp: Instant,
    val containerId: String,
    val jobId: UUID
) : DockerContainerBatchUpdateEvent

data class DockerContainerBatchUpdateContainerFinished(
    override val batchJobId: UUID,
    override val timestamp: Instant,
    val containerId: String,
    val jobId: UUID?,
    val succeeded: Boolean,
    val reason: String?
) : DockerContainerBatchUpdateEvent

data class DockerContainerBatchUpdateContainerSkipped(
    override val batchJobId: UUID,
    override val timestamp: Instant,
    val containerId: String
) : DockerContainerBatchUpdateEvent

data class DockerContainerBatchUpdateFinished(
    override val batchJobId: UUID,
    override val timestamp: Instant,
    val aborted: Boolean
) : DockerContainerBatchUpdateEvent
