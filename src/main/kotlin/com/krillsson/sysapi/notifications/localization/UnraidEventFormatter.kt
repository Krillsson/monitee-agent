package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.unraid.UnraidVmService
import org.springframework.stereotype.Component

@Component
class UnraidEventFormatter(
    private val unraidVmService: UnraidVmService,
) {
    fun formatVmRunningOngoingDescription(monitoredItemId: String?, formattedValue: String): String =
        "${displayName(monitoredItemId)} is $formattedValue"

    fun formatVmRunningResolvedDescription(
        monitoredItemId: String?,
        formattedValue: String,
        formattedDuration: String,
    ): String =
        "${displayName(monitoredItemId)} is $formattedValue again after $formattedDuration"

    private fun displayName(monitoredItemId: String?): String =
        monitoredItemId?.let { unraidVmService.vm(it) }?.name ?: "VM ${monitoredItemId.orEmpty().take(8)}"
}
