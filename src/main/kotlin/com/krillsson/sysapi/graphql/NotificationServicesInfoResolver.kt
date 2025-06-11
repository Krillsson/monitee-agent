package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.notifications.NotificationManager
import com.krillsson.sysapi.notifications.NotificationServiceInfo
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class NotificationServicesInfoResolver(private val notificationManager: NotificationManager) {
    @QueryMapping
    fun notificationServices(): NotificationServiceInfo {
        return notificationManager.notificationServiceInfo()
    }
}