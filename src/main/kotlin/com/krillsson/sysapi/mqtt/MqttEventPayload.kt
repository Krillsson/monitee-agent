package com.krillsson.sysapi.mqtt

data class MqttEventPayload(
    val title: String,
    val message: String,
    val priority: Int,
    val clickUrl: String,
    val eventType: String,
    val monitorType: String?,
    val timestamp: String,
    val serverName: String,
    val serverId: String
)
