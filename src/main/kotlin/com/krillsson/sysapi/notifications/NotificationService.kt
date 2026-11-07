package com.krillsson.sysapi.notifications

interface NotificationService {
    val enabled: Boolean
    val channels: List<String>
    fun notify(notification: NotificationParameters)
}