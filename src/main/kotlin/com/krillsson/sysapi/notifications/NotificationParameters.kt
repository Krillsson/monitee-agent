package com.krillsson.sysapi.notifications

data class NotificationParameters(
    val title: String,
    val message: String,
    val clickUrl: String,
    val priority: Int
)