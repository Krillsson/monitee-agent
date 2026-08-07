package com.krillsson.sysapi.mqtt

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.mqtt.homeassistant.HomeAssistantDiscovery
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class MqttPublisher(
    configFile: YAMLConfigFile,
    private val connection: MqttConnection,
    private val collector: MqttEntityCollector,
    private val discovery: HomeAssistantDiscovery,
    private val topics: MqttTopics,
    private val mapper: ObjectMapper,
    private val taskScheduler: TaskScheduler
) {

    private val logger by logger()

    private val config = configFile.mqtt

    @Volatile
    private var publishedEntities: Map<String, MqttEntity> = emptyMap()

    @Volatile
    private var generation = -1

    @PostConstruct
    fun start() {
        if (!connection.enabled) {
            return
        }
        val interval = Duration.ofSeconds(config.intervalSeconds.coerceAtLeast(MINIMUM_INTERVAL_SECONDS))
        logger.info("Publishing metrics to MQTT as {} every {}", topics.state, interval)
        taskScheduler.scheduleAtFixedRate(this::run, interval)
    }

    fun run() {
        try {
            connection.ensureConnected()
            if (!connection.connected) {
                return
            }
            val measured = collector.collect()
            publishDiscovery(measured)
            publishState(measured)
        } catch (e: Exception) {
            logger.error("Failed to publish metrics to MQTT", e)
        }
    }

    private fun publishDiscovery(measured: List<MeasuredEntity>) {
        if (!config.homeAssistant.enabled) {
            return
        }
        if (generation != connection.generation) {
            generation = connection.generation
            publishedEntities = emptyMap()
        }
        val current = measured.associate { it.entity.key to it.entity }
        measured.filterNot { discovery.configTopic(it.entity) == publishedEntities[it.entity.key]?.let(discovery::configTopic) }
            .forEach { entity ->
                clearOtherComponents(entity.entity)
                connection.publish(
                    discovery.configTopic(entity.entity),
                    mapper.writeValueAsString(discovery.configPayload(entity)),
                    retain = true
                )
            }
        publishedEntities.filterKeys { !current.containsKey(it) }.values.forEach { entity ->
            connection.publish(discovery.configTopic(entity), "", retain = true)
        }
        publishedEntities = current
    }

    /**
     * A key that moves between sensor and binary sensor lands on a different discovery topic, and
     * the retained config on the old one would leave Home Assistant showing the entity twice.
     */
    private fun clearOtherComponents(entity: MqttEntity) {
        MqttEntity.Component.entries.filter { it != entity.component }.forEach { component ->
            connection.publish(discovery.configTopic(entity.copy(component = component)), "", retain = true)
        }
    }

    private fun publishState(measured: List<MeasuredEntity>) {
        val payload = linkedMapOf<String, Any?>()
        measured.forEach { entity ->
            payload[entity.entity.key] = entity.value
            entity.attributes?.let { payload["${entity.entity.key}_attributes"] = it }
        }
        connection.publish(topics.state, mapper.writeValueAsString(payload), retain = true)
    }

    companion object {
        private const val MINIMUM_INTERVAL_SECONDS = 5L
    }
}
