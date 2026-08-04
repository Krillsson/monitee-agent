package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep
import com.krillsson.sysapi.core.domain.docker.ImagePullLayer
import java.time.Instant
import java.util.UUID

interface DockerContainerUpdateEvent {
    val jobId: UUID
    val containerId: String
    val timestamp: Instant
}

data class DockerContainerUpdateStepChanged(
    override val jobId: UUID,
    override val containerId: String,
    override val timestamp: Instant,
    val step: ContainerUpdateStep
) : DockerContainerUpdateEvent

data class DockerContainerUpdateImagePullProgress(
    override val jobId: UUID,
    override val containerId: String,
    override val timestamp: Instant,
    val imageRef: String,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val layers: List<ImagePullLayer>
) : DockerContainerUpdateEvent

data class DockerContainerUpdateSucceeded(
    override val jobId: UUID,
    override val containerId: String,
    override val timestamp: Instant,
    val newContainerId: String,
    val composeProject: String?
) : DockerContainerUpdateEvent

data class DockerContainerUpdateFailed(
    override val jobId: UUID,
    override val containerId: String,
    override val timestamp: Instant,
    val step: ContainerUpdateStep,
    val reason: String,
    val rolledBack: Boolean
) : DockerContainerUpdateEvent
