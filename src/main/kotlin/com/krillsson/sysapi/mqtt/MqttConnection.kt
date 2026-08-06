package com.krillsson.sysapi.mqtt

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class MqttConnection(configFile: YAMLConfigFile, private val topics: MqttTopics) {

    private val logger by logger()

    private val config = configFile.mqtt

    private val qos = config.qos.coerceIn(0, 2)

    private val connecting = AtomicBoolean(false)

    private val failureReported = AtomicBoolean(false)

    private var client: MqttAsyncClient? = null

    @Volatile
    private var connectionGeneration: Int = 0

    val generation: Int
        get() = connectionGeneration

    val enabled: Boolean
        get() = client != null

    val connected: Boolean
        get() = client?.isConnected == true

    @PostConstruct
    fun start() {
        if (!config.enabled) {
            return
        }
        if (config.url.isBlank()) {
            logger.error("MQTT is enabled but no broker url is configured")
            return
        }
        val client = try {
            MqttAsyncClient(config.url, clientId(), MemoryPersistence())
        } catch (e: MqttException) {
            logger.error("MQTT is enabled but ${config.url} is not a usable broker url: ${e.message}")
            return
        } catch (e: IllegalArgumentException) {
            logger.error("MQTT is enabled but ${config.url} is not a usable broker url: ${e.message}")
            return
        }
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                logger.info("Connected to MQTT broker at {}", serverURI)
                connectionGeneration++
                failureReported.set(false)
                publish(topics.status, MqttTopics.ONLINE, retain = true)
            }

            override fun connectionLost(cause: Throwable?) {
                logger.warn("Lost the connection to the MQTT broker: ${cause?.message}")
            }

            override fun messageArrived(topic: String, message: MqttMessage) = Unit

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        this.client = client
        connect(client)
    }

    @PreDestroy
    fun stop() {
        val client = client ?: return
        try {
            if (client.isConnected) {
                client.publish(topics.status, MqttTopics.OFFLINE.toByteArray(), 1, true)
                    .waitForCompletion(SHUTDOWN_TIMEOUT_MILLIS)
                client.disconnect().waitForCompletion(SHUTDOWN_TIMEOUT_MILLIS)
            }
            client.close()
        } catch (e: MqttException) {
            logger.warn("Failed to disconnect from the MQTT broker: ${e.message}")
        }
    }

    fun ensureConnected() {
        val client = client ?: return
        if (client.isConnected || connecting.get()) {
            return
        }
        connect(client)
    }

    fun publish(topic: String, payload: String, retain: Boolean) {
        val client = client ?: return
        if (!client.isConnected) {
            return
        }
        try {
            client.publish(topic, payload.toByteArray(Charsets.UTF_8), qos, retain)
        } catch (e: MqttException) {
            logger.warn("Failed to publish to $topic: ${e.message}")
        }
    }

    private fun connect(client: MqttAsyncClient) {
        if (!connecting.compareAndSet(false, true)) {
            return
        }
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = CONNECT_TIMEOUT_SECONDS
            keepAliveInterval = KEEP_ALIVE_SECONDS
            config.username?.takeIf { it.isNotBlank() }?.let { userName = it }
            config.password?.takeIf { it.isNotEmpty() }?.let { password = it.toCharArray() }
            setWill(topics.status, MqttTopics.OFFLINE.toByteArray(), 1, true)
        }
        try {
            client.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    connecting.set(false)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    connecting.set(false)
                    reportFailure(exception?.message)
                }
            })
        } catch (e: MqttException) {
            connecting.set(false)
            reportFailure(e.message)
        }
    }

    private fun reportFailure(message: String?) {
        val text = "Failed to connect to the MQTT broker at ${config.url}: $message"
        if (failureReported.compareAndSet(false, true)) {
            logger.warn(text)
        } else {
            logger.debug(text)
        }
    }

    private fun clientId() = config.clientId?.takeIf { it.isNotBlank() } ?: topics.nodeId

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 10
        private const val KEEP_ALIVE_SECONDS = 60
        private const val SHUTDOWN_TIMEOUT_MILLIS = 2000L
    }
}
