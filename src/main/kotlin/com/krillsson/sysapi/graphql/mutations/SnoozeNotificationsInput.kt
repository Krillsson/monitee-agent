package com.krillsson.sysapi.graphql.mutations

import java.time.Instant

data class SnoozeNotificationsInput(
    val until: Instant?,
    val reason: String?
)
