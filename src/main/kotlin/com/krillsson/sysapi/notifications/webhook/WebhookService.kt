package com.krillsson.sysapi.notifications.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.NotificationsConfiguration.WebhookConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.notifications.NotificationParameters
import com.krillsson.sysapi.notifications.NotificationService
import com.krillsson.sysapi.notifications.WebhookInfo
import com.krillsson.sysapi.util.logger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service
class WebhookService(
    yamlConfigFile: YAMLConfigFile,
    private val template: WebhookTemplate,
    private val mapper: ObjectMapper
) : NotificationService {

    private val logger by logger()

    private val configured = yamlConfigFile.notifications.webhooks

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val webhooks = configured
        .filter { it.enabled }
        .filter { it.hasUsableUrl() }
        .map { it to client.newBuilder().callTimeout(it.timeoutSeconds, TimeUnit.SECONDS).build() }

    override val enabled: Boolean
        get() = webhooks.isNotEmpty()

    override fun notify(notification: NotificationParameters) {
        for ((config, client) in webhooks) {
            send(config, client, notification)
        }
    }

    fun webhookInfo(): List<WebhookInfo> {
        return configured.map { WebhookInfo(name = it.displayName(), enabled = it.enabled) }
    }

    private fun send(config: WebhookConfiguration, client: OkHttpClient, notification: NotificationParameters) {
        val name = config.displayName()
        val method = config.method.uppercase()
        val request = try {
            Request.Builder()
                .url(template.renderUrl(config.url, notification))
                .method(method, bodyFor(config, notification, method))
                .apply {
                    if (!config.username.isNullOrBlank()) {
                        header("Authorization", Credentials.basic(config.username, config.password.orEmpty()))
                    }
                    config.headers.forEach { (key, value) -> header(key, value) }
                }
                .build()
        } catch (e: IllegalArgumentException) {
            logger.error("Webhook $name is misconfigured and was skipped: ${e.message}")
            return
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logger.error("Failed to send notification to webhook $name", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        logger.debug("Successfully sent notification to webhook $name: ${it.code}")
                    } else {
                        logger.error(
                            "Failed to send notification to webhook $name: ${it.code} ${it.message} ${
                                it.peekBody(ERROR_BODY_LIMIT_BYTES).string()
                            }"
                        )
                    }
                }
            }
        })
    }

    private fun bodyFor(config: WebhookConfiguration, notification: NotificationParameters, method: String) =
        if (method in METHODS_WITHOUT_BODY) {
            null
        } else {
            val body = config.body?.let { template.renderBody(it, notification, config.contentType) }
                ?: mapper.writeValueAsString(notification.asPayload())
            body.toRequestBody(config.contentType.toMediaTypeOrNull())
        }

    private fun NotificationParameters.asPayload() = WebhookPayload(
        title = title,
        message = message,
        priority = priority,
        clickUrl = clickUrl,
        eventType = eventType,
        monitorType = monitorType,
        timestamp = timestamp.toString(),
        serverName = serverName,
        serverId = serverId
    )

    private fun WebhookConfiguration.hasUsableUrl(): Boolean {
        val usable = url.toHttpUrlOrNull() != null
        if (!usable) {
            logger.error("Webhook ${displayName()} was ignored because its url is not a valid http or https url")
        }
        return usable
    }

    private fun WebhookConfiguration.displayName() =
        name?.takeIf { it.isNotBlank() } ?: url.toHttpUrlOrNull()?.host ?: "webhook"

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val ERROR_BODY_LIMIT_BYTES = 512L
        private val METHODS_WITHOUT_BODY = setOf("GET", "HEAD")
    }
}
