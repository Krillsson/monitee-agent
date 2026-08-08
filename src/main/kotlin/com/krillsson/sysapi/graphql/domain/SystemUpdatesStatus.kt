package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.core.domain.system.PendingPackage
import java.time.Instant

interface SystemUpdates

data class SystemUpdatesAvailable(
    val manager: String,
    val totalCount: Int,
    val securityCount: Int?,
    val packages: List<PendingPackage>,
    val checkedAt: Instant
) : SystemUpdates

data class SystemUpdatesUnavailable(
    val reason: String,
    val isDisabled: Boolean
) : SystemUpdates
