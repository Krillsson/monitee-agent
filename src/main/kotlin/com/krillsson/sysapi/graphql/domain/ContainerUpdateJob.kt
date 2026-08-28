package com.krillsson.sysapi.graphql.domain

import java.time.Instant
import java.util.UUID

enum class DockerContainerUpdateJobState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class DockerContainerUpdateJob(
    val jobId: UUID,
    val containerId: String,
    val state: DockerContainerUpdateJobState,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val lastEvent: DockerContainerUpdateEvent?
)
