package com.krillsson.sysapi.notifications.ntfy

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.notifications.NotificationAction
import com.krillsson.sysapi.notifications.NotificationEmoji
import com.krillsson.sysapi.notifications.NotificationParameters
import com.krillsson.sysapi.notifications.NotificationService
import com.krillsson.sysapi.notifications.NtfyInfo
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.util.logger
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class NtfyService(
    yamlConfigFile: YAMLConfigFile,
    private val ntfyApi: NtfyApi,
    private val serverIdService: ServerIdService
) : NotificationService {

    private val logger by logger()

    private val config = yamlConfigFile.notifications.ntfy

    override val enabled: Boolean
        get() = config.enabled

    private val topic = config.topic ?: "$TOPIC_PREFIX-${serverIdService.serverId}"

    private val authorization: String? = when {
        !config.token.isNullOrBlank() -> "Bearer ${config.token}"
        !config.username.isNullOrBlank() -> Credentials.basic(config.username, config.password.orEmpty())
        else -> null
    }

    override fun notify(notification: NotificationParameters) {
        sendNotification(
            title = notification.title,
            message = notification.message,
            priority = notification.priority,
            clickUrl = notification.clickUrl,
            topic = topic,
            tags = if (config.emoji) NotificationEmoji.ntfyTags(notification) else emptyList(),
            actions = if (config.actions) notification.actions.map { it.toNtfyAction() } else emptyList()
        )
    }

    fun sendNotification(
        title: String,
        message: String,
        priority: Int = 3,
        clickUrl: String? = null,
        topic: String,
        tags: List<String> = emptyList(),
        actions: List<NtfyApi.Notification.Action> = emptyList()
    ) {
        val notification = NtfyApi.Notification(
            title = title,
            topic = topic,
            message = message,
            priority = priority,
            clickUrl = clickUrl,
            iconUrl = "https://monitee.app/logo/logo.png",
            tags = tags.takeIf { it.isNotEmpty() },
            actions = actions.takeIf { it.isNotEmpty() }
        )

        try {
            val response = ntfyApi.sendNotification(notification, authorization).execute()

            if (response.isSuccessful) {
                logger.debug("Successfully sent notification: ${response.code()} ${response.body()?.string()}")
            } else {
                logger.error("Failed to send notification: ${response.code()} ${response.errorBody()?.string()}")
            }
        } catch (e: IOException) {
            // Log the error
            logger.error("Failed to send notification", e)
        }
    }

    fun ntfyInfo(): NtfyInfo {
        return NtfyInfo(
            config.enabled,
            "ntfy://${config.url.toHttpUrl().host}/$topic",
            topic,
        )
    }

    private fun NotificationAction.toNtfyAction(): NtfyApi.Notification.Action = when (this) {
        is NotificationAction.View -> NtfyApi.Notification.Action(
            action = "view",
            label = label,
            url = url,
            clear = clear
        )
    }

    companion object {
        private const val TOPIC_PREFIX = "monitee-agent"
    }
}