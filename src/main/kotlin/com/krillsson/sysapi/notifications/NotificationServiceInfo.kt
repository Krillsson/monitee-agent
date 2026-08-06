package com.krillsson.sysapi.notifications

data class NotificationServiceInfo(
    val serverId: String,
    val serverName: String,
    val ntfy: NtfyInfo,
    val webhooks: List<WebhookInfo>,
    val mqtt: MqttInfo
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

data class MqttInfo(
    val enabled: Boolean,
    val connected: Boolean,
    val url: String,
    val stateTopic: String,
    val eventTopic: String
)
