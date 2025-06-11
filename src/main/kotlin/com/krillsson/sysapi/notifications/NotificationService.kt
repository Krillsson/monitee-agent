package com.krillsson.sysapi.notifications

interface NotificationService {
    val enabled: Boolean
    fun notify(notification: NotificationParameters)
}