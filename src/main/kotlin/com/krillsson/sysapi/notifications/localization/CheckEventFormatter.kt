package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.check.CheckService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CheckEventFormatter(
    private val checkService: CheckService,
) {
    fun formatWebServerUpOngoingDescription(monitoredItemId: String?): String =
        "${displayName(monitoredItemId)} is failing its check"

    fun formatCheckLatencyOngoingDescription(
        monitoredItemId: String?,
        formattedValue: String,
        formattedThreshold: String,
    ): String =
        "${displayName(monitoredItemId)} answered in $formattedValue, above $formattedThreshold"

    fun formatWebServerUpResolvedDescription(
        monitoredItemId: String?,
        formattedDuration: String,
    ): String =
        "${displayName(monitoredItemId)} is passing its check again after $formattedDuration"

    fun formatCheckLatencyResolvedDescription(
        monitoredItemId: String?,
        formattedThreshold: String,
        formattedValue: String,
        formattedDuration: String,
    ): String =
        "${displayName(monitoredItemId)} is back below $formattedThreshold at $formattedValue after $formattedDuration"

    private fun displayName(monitoredItemId: String?): String? =
        monitoredItemId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { checkService.getById(it) }
            ?.name
            ?: monitoredItemId
}
