package com.krillsson.sysapi.docker

sealed interface RecreateContainerResult {
    /**
     * [composeProject] is set when the container carries compose labels: it keeps running with the
     * new image, but no longer matches what its compose file describes until the next `compose up`.
     */
    data class Success(val containerId: String, val composeProject: String?) : RecreateContainerResult
    data class Failed(val reason: String) : RecreateContainerResult
    object Unavailable : RecreateContainerResult
}
