package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.storagepool.StoragePoolService
import org.springframework.stereotype.Component

@Component
class StoragePoolEventFormatter(
    private val storagePoolService: StoragePoolService,
) {
    fun formatOngoingDescription(monitoredItemId: String?, formattedValue: String): String =
        "Pool ${displayName(monitoredItemId)} is $formattedValue"

    fun formatResolvedDescription(
        monitoredItemId: String?,
        formattedValue: String,
        formattedDuration: String,
    ): String =
        "Pool ${displayName(monitoredItemId)} is back to $formattedValue after $formattedDuration"

    private fun displayName(monitoredItemId: String?): String? =
        monitoredItemId?.let { storagePoolService.displayName(it) } ?: monitoredItemId
}
