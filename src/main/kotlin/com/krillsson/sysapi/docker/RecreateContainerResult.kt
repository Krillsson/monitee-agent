package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ContainerUpdateStep

sealed interface RecreateContainerResult {
    /**
     * [composeProject] is set when the container carries compose labels: it keeps running with the
     * new image, but no longer matches what its compose file describes until the next `compose up`.
     */
    data class Success(val containerId: String, val composeProject: String?) : RecreateContainerResult
    data class Failed(
        val step: ContainerUpdateStep,
        val reason: String,
        val rolledBack: Boolean
    ) : RecreateContainerResult
}
