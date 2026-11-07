package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.notifications.TestNotificationResult
import com.krillsson.sysapi.notifications.TestNotificationService
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class TestNotificationMutationResolver(private val testNotificationService: TestNotificationService) {

    @MutationMapping
    fun sendTestNotification(): TestNotificationResult {
        return testNotificationService.send()
    }
}
