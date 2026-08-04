package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.core.domain.docker.CommandType
import java.util.UUID

interface PerformDockerContainerCommandOutput

data class PerformDockerContainerCommandOutputSucceeded(
        val containerId: String
) : PerformDockerContainerCommandOutput

data class PerformDockerContainerCommandOutputFailed(
        val reason: String
) : PerformDockerContainerCommandOutput

data class PerformDockerContainerCommandInput(
        val containerId: String,
        val command: CommandType
)

interface UpdateDockerContainerOutput

data class UpdateDockerContainerOutputStarted(
        val jobId: UUID,
        val containerId: String
) : UpdateDockerContainerOutput

data class UpdateDockerContainerOutputFailed(
        val reason: String
) : UpdateDockerContainerOutput

data class UpdateDockerContainerInput(
        val containerId: String,
        val pullImage: Boolean
)


