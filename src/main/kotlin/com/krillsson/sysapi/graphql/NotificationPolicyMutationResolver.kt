package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.mutations.NotificationPolicyInput
import com.krillsson.sysapi.notifications.NotificationPolicy
import com.krillsson.sysapi.notifications.NotificationPolicyService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class NotificationPolicyMutationResolver(private val notificationPolicyService: NotificationPolicyService) {

    @MutationMapping
    fun updateNotificationPolicy(@Argument input: NotificationPolicyInput): NotificationPolicy {
        return notificationPolicyService.update(input.asUpdate())
    }
}
