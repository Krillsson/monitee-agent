package com.krillsson.sysapi.notifications

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.persistence.JsonFile
import org.springframework.stereotype.Component
import java.time.DayOfWeek

@Component
class NotificationPolicyStore(objectMapper: ObjectMapper) :
    JsonFile<NotificationPolicyStore.StoredNotificationPolicy>(fileName, TYPE_REFERENCE, objectMapper) {
    companion object {
        val TYPE_REFERENCE: TypeReference<StoredNotificationPolicy> =
            object : TypeReference<StoredNotificationPolicy>() {}

        const val fileName = "notification_policy.json"
    }

    data class StoredNotificationPolicy(
        val quietHours: StoredQuietHours? = null,
        val cooldownMinutes: Int? = null,
        val notifyOnRecovery: Boolean? = null
    ) {
        data class StoredQuietHours(
            val enabled: Boolean? = null,
            val start: String? = null,
            val end: String? = null,
            val days: List<DayOfWeek>? = null,
            val timeZone: String? = null,
            val allowCritical: Boolean? = null
        )
    }
}
