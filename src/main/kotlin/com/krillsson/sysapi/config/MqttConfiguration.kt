package com.krillsson.sysapi.config

data class MqttConfiguration(
    val enabled: Boolean = false,
    val url: String = "",
    val clientId: String? = null,
    val username: String? = null,
    val password: String? = null,
    val topicPrefix: String = "monitee",
    val intervalSeconds: Long = 30,
    val qos: Int = 0,
    val emoji: Boolean = true,
    val containers: Boolean = false,
    val networkInterfaces: Boolean = true,
    val homeAssistant: HomeAssistantConfiguration = HomeAssistantConfiguration()
) {
    data class HomeAssistantConfiguration(
        val enabled: Boolean = true,
        val discoveryPrefix: String = "homeassistant"
    )
}
