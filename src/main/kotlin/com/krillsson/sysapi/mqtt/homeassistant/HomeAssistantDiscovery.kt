package com.krillsson.sysapi.mqtt.homeassistant

import com.krillsson.sysapi.BuildConfig
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.mqtt.MeasuredEntity
import com.krillsson.sysapi.mqtt.MqttEntity
import com.krillsson.sysapi.mqtt.MqttTopics
import com.krillsson.sysapi.util.EnvironmentUtils
import org.springframework.stereotype.Component

@Component
class HomeAssistantDiscovery(
    configFile: YAMLConfigFile,
    private val topics: MqttTopics,
    private val metrics: Metrics
) {

    private val serverName = configFile.notifications.serverName ?: EnvironmentUtils.hostName

    private val device: Map<String, Any?> by lazy { device() }

    private val origin = mapOf(
        "name" to "monitee-agent",
        "sw_version" to BuildConfig.APP_VERSION,
        "support_url" to "https://github.com/Krillsson/monitee-agent"
    )

    fun configTopic(entity: MqttEntity) = topics.discoveryConfig(entity)

    fun configPayload(measured: MeasuredEntity): Map<String, Any?> {
        val entity = measured.entity
        val payload = mutableMapOf<String, Any?>(
            "name" to entity.name,
            "unique_id" to "${topics.nodeId}_${entity.key}",
            "state_topic" to topics.state,
            "value_template" to valueTemplate(entity.key),
            "availability_topic" to topics.status,
            "payload_available" to MqttTopics.ONLINE,
            "payload_not_available" to MqttTopics.OFFLINE,
            "unit_of_measurement" to entity.unit,
            "device_class" to entity.deviceClass,
            "state_class" to entity.stateClass,
            "suggested_display_precision" to entity.precision,
            "entity_category" to if (entity.diagnostic) "diagnostic" else null,
            "device" to device,
            "origin" to origin
        )
        if (entity.component == MqttEntity.Component.BINARY_SENSOR) {
            payload["payload_on"] = ON
            payload["payload_off"] = OFF
        }
        if (measured.attributes != null) {
            payload["json_attributes_topic"] = topics.state
            payload["json_attributes_template"] = attributesTemplate(entity.key)
        }
        return payload.filterValues { it != null }
    }

    private fun valueTemplate(key: String) =
        "{{ value_json.$key if value_json.$key is not none else '$NONE' }}"

    private fun attributesTemplate(key: String) = "{{ value_json.${key}_attributes | tojson }}"

    private fun device(): Map<String, Any?> {
        val info = metrics.systemInfo()
        val computerSystem = info.motherboard.computerSystem
        return mapOf(
            "identifiers" to listOf(topics.nodeId),
            "name" to serverName,
            "manufacturer" to computerSystem.manufacturer.usable(),
            "model" to computerSystem.model.usable(),
            "sw_version" to "${info.operatingSystem.family} ${info.operatingSystem.versionInfo.version}".trim()
        ).filterValues { it != null }
    }

    private fun String?.usable() = this?.trim()?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }

    companion object {
        const val ON = "ON"
        const val OFF = "OFF"
        private const val NONE = "None"
    }
}
