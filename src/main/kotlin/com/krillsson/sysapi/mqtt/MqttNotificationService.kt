package com.krillsson.sysapi.mqtt

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.notifications.MqttInfo
import com.krillsson.sysapi.notifications.NotificationEmoji
import com.krillsson.sysapi.notifications.NotificationParameters
import com.krillsson.sysapi.notifications.NotificationService
import org.springframework.stereotype.Service

@Service
class MqttNotificationService(
    configFile: YAMLConfigFile,
    private val connection: MqttConnection,
    private val topics: MqttTopics,
    private val mapper: ObjectMapper
) : NotificationService {

    private val config = configFile.mqtt

    override val enabled: Boolean
        get() = connection.enabled

    override fun notify(notification: NotificationParameters) {
        val parameters = if (config.emoji) NotificationEmoji.decorate(notification) else notification
        connection.publish(topics.event, mapper.writeValueAsString(parameters.asPayload()), retain = false)
    }

    fun mqttInfo() = MqttInfo(
        enabled = connection.enabled,
        connected = connection.connected,
        url = config.url,
        stateTopic = topics.state,
        eventTopic = topics.event
    )

    private fun NotificationParameters.asPayload() = MqttEventPayload(
        title = title,
        message = message,
        priority = priority,
        clickUrl = clickUrl,
        eventType = eventType.name,
        monitorType = monitorType?.name,
        timestamp = timestamp.toString(),
        serverName = serverName,
        serverId = serverId
    )
}
