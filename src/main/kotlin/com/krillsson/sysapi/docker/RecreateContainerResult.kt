package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep

sealed interface RecreateContainerResult {
    data class Success(val containerId: String, val composeProject: String?) : RecreateContainerResult
    data class Failed(
        val step: ContainerUpdateStep,
        val reason: String,
        val rolledBack: Boolean
    ) : RecreateContainerResult
}
