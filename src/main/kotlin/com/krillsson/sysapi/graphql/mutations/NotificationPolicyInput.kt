package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.notifications.NotificationPolicyUpdate
import com.krillsson.sysapi.notifications.QuietHoursUpdate
import java.time.DayOfWeek

data class NotificationPolicyInput(
    val quietHours: QuietHoursInput?,
    val cooldownMinutes: Int?,
    val notifyOnRecovery: Boolean?
) {
    fun asUpdate() = NotificationPolicyUpdate(
        quietHours = quietHours?.asUpdate(),
        cooldownMinutes = cooldownMinutes,
        notifyOnRecovery = notifyOnRecovery
    )
}

data class QuietHoursInput(
    val enabled: Boolean?,
    val start: String?,
    val end: String?,
    val days: List<DayOfWeek>?,
    val timeZone: String?,
    val allowCritical: Boolean?
) {
    fun asUpdate() = QuietHoursUpdate(
        enabled = enabled,
        start = start,
        end = end,
        days = days,
        timeZone = timeZone,
        allowCritical = allowCritical
    )
}
