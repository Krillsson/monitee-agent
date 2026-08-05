package com.krillsson.sysapi.core.domain.system

import java.time.Instant

data class SystemUpdates(
    val manager: String,
    val totalCount: Int,
    val securityCount: Int?,
    val packages: List<PendingPackage>,
    val checkedAt: Instant
)

data class PendingPackage(
    val name: String,
    val currentVersion: String?,
    val newVersion: String?,
    val isSecurity: Boolean?
)
