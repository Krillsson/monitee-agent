package com.krillsson.sysapi.core.domain.docker

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

enum class ImagePullLayerPhase {
    WAITING,
    DOWNLOADING,
    DOWNLOADED,
    EXTRACTING,
    COMPLETE,
    ALREADY_PRESENT;

    val isDownloaded: Boolean
        get() = this != WAITING && this != DOWNLOADING

    val isExtracted: Boolean
        get() = this == COMPLETE || this == ALREADY_PRESENT
}

data class ImagePullLayer(
    val id: String,
    val status: String,
    val phase: ImagePullLayerPhase,
    val downloadedBytes: Long?,
    val downloadTotalBytes: Long?,
    val extractedBytes: Long?,
    val extractTotalBytes: Long?,
    val extractElapsedSeconds: Long?
)
