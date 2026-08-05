package com.krillsson.sysapi.notifications

data class NotificationServiceInfo(
    val serverId: String,
    val serverName: String,
    val ntfy: NtfyInfo,
    val webhooks: List<WebhookInfo>
)

data class NtfyInfo(
    val enabled: Boolean,
    val ntfyAppTopicDeeplink: String,
    val topic: String,
)

data class WebhookInfo(
    val name: String,
    val enabled: Boolean
)