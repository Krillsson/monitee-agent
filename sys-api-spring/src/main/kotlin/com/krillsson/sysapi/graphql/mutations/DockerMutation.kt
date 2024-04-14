package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.core.domain.docker.CommandType

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


