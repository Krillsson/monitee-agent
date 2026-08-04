package com.krillsson.sysapi.core.domain.docker

/**
 * The steps a container update runs through, in the order they happen. Steps with nothing to do
 * are skipped rather than reported as instant.
 */
enum class ContainerUpdateStep {
    INSPECTING_CONTAINER,
    PULLING_IMAGE,
    STOPPING_CONTAINER,
    RENAMING_CONTAINER,
    DISCONNECTING_NETWORKS,
    CREATING_CONTAINER,
    CONNECTING_NETWORKS,
    STARTING_CONTAINER,
    MOVING_MONITORS_AND_HISTORY,
    REMOVING_REPLACED_CONTAINER,
    ROLLING_BACK
}

data class ImagePullLayer(
    val id: String,
    val status: String,
    val currentBytes: Long?,
    val totalBytes: Long?
)
