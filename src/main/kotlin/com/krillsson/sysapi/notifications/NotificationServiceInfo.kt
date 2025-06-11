package com.krillsson.sysapi.notifications

data class NotificationServiceInfo(
    val serverId: String,
    val ntfy: NtfyInfo
)

data class NtfyInfo(
    val enabled: Boolean,
    val ntfyAppTopicDeeplink: String,
    val topic: String,
)