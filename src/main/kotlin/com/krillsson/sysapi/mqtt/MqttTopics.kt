package com.krillsson.sysapi.mqtt

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.serverid.ServerIdService
import org.springframework.stereotype.Component

@Component
class MqttTopics(configFile: YAMLConfigFile, private val serverIdService: ServerIdService) {

    private val config = configFile.mqtt

    private val prefix = config.topicPrefix.trim('/').ifBlank { "monitee" }

    private val discoveryPrefix = config.homeAssistant.discoveryPrefix.trim('/').ifBlank { "homeassistant" }

    val serverId: String by lazy { serverIdService.serverId.toString() }

    val nodeId: String by lazy { "monitee_${serverId.replace("-", "")}" }

    val status: String by lazy { "$prefix/$serverId/status" }

    val state: String by lazy { "$prefix/$serverId/state" }

    val event: String by lazy { "$prefix/$serverId/event" }

    fun discoveryConfig(entity: MqttEntity) = "$discoveryPrefix/${entity.component.id}/$nodeId/${entity.key}/config"

    companion object {
        const val ONLINE = "online"
        const val OFFLINE = "offline"
    }
}
