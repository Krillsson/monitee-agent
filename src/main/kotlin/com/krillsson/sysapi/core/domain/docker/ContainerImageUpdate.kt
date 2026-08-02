package com.krillsson.sysapi.core.domain.docker

import java.time.Instant

enum class ImageUpdateStatus {
    UP_TO_DATE,
    OUTDATED,
    SKIPPED,
    ERROR,
    UNKNOWN
}

data class ContainerImageUpdate(
    val containerId: String,
    val imageRef: String,
    val status: ImageUpdateStatus,
    val remoteDigest: String?,
    val reason: String?,
    val checkedAt: Instant?
) {
    companion object {
        fun notCheckedYet(containerId: String, imageRef: String) = ContainerImageUpdate(
            containerId = containerId,
            imageRef = imageRef,
            status = ImageUpdateStatus.UNKNOWN,
            remoteDigest = null,
            reason = null,
            checkedAt = null
        )
    }
}
