package com.krillsson.sysapi.notifications.ntfy

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.notifications.NotificationParameters
import com.krillsson.sysapi.notifications.NotificationService
import com.krillsson.sysapi.notifications.NtfyInfo
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.util.logger
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


    override fun notify(notification: NotificationParameters) {
        sendNotification(
            title = notification.title,
            message = notification.message,
            priority = notification.priority,
            clickUrl = notification.clickUrl,
            topic = topic,
        )
    }

    fun sendNotification(
        title: String,
        message: String,
        priority: Int = 3,
        clickAction: NtfyApi.Notification.Action? = null,
        clickUrl: String? = null,
        topic: String,
        tags: List<String> = emptyList()
    ) {
        val notification = NtfyApi.Notification(
            title = title,
            topic = topic,
            message = message,
            priority = priority,
            clickUrl = clickUrl,
            iconUrl = "https://monitee.app/logo/logo.png",
            tags = tags.takeIf { it.isNotEmpty() }
        )

        try {
            val response = ntfyApi.sendNotification(notification).execute()

            if (!response.isSuccessful) {
                logger.error("Failed to send notification: ${response.code()} ${response.errorBody()?.string()}")
            } else {
                logger.info("Successfully sent notification: ${response.code()} ${response.body()?.string()}")
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

    companion object {
        private const val TOPIC_PREFIX = "monitee-agent"
    }
}