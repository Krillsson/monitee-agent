package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.mutations.SnoozeNotificationsInput
import com.krillsson.sysapi.notifications.SnoozeNotificationsInfo
import com.krillsson.sysapi.notifications.SnoozeNotificationsService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class NotificationSnoozeMutationResolver(private val snoozeNotificationsService: SnoozeNotificationsService) {

    @MutationMapping
    fun snoozeNotifications(@Argument input: SnoozeNotificationsInput): SnoozeNotificationsInfo {
        return snoozeNotificationsService.snooze(input.until, input.reason)
    }

    @MutationMapping
    fun resumeNotifications(): SnoozeNotificationsInfo {
        return snoozeNotificationsService.resume()
    }
}
