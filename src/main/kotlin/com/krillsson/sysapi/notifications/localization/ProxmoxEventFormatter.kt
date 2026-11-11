package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.proxmox.ProxmoxService
import org.springframework.stereotype.Component

@Component
class ProxmoxEventFormatter(
    private val proxmoxService: ProxmoxService,
) {
    fun formatGuestRunningOngoingDescription(monitoredItemId: String?, formattedValue: String): String =
        "${displayName(monitoredItemId)} is $formattedValue"

    fun formatGuestRunningResolvedDescription(
        monitoredItemId: String?,
        formattedValue: String,
        formattedDuration: String,
    ): String =
        "${displayName(monitoredItemId)} is $formattedValue again after $formattedDuration"

    fun formatGuestUsageOngoingDescription(monitoredItemId: String?, resource: String, change: String): String =
        "${displayName(monitoredItemId)} $resource usage $change"

    fun formatGuestUsageResolvedDescription(
        monitoredItemId: String?,
        resource: String,
        formattedThreshold: String,
        formattedValue: String,
        formattedDuration: String,
    ): String =
        "${displayName(monitoredItemId)} $resource usage is back below $formattedThreshold at $formattedValue after $formattedDuration"

    private fun displayName(monitoredItemId: String?): String =
        monitoredItemId?.let { proxmoxService.guest(it) }?.let { "${it.name} (${it.vmid})" } ?: "Guest $monitoredItemId"
}
