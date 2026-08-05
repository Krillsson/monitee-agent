package com.krillsson.sysapi.notifications.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.notifications.NotificationParameters
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class WebhookTemplate(private val mapper: ObjectMapper) {

    fun renderBody(template: String, notification: NotificationParameters, contentType: String): String {
        return render(template, notification) { escapeForContentType(it, contentType) }
    }

    fun renderUrl(template: String, notification: NotificationParameters): String {
        return render(template, notification) { URLEncoder.encode(it, StandardCharsets.UTF_8) }
    }

    private fun render(
        template: String,
        notification: NotificationParameters,
        escape: (String) -> String
    ): String {
        return PLACEHOLDER.replace(template) { match ->
            val value = notification.valueOf(match.groupValues[1]) ?: return@replace match.value
            escape(value)
        }
    }

    private fun escapeForContentType(value: String, contentType: String) = when {
        contentType.contains("json", ignoreCase = true) ->
            mapper.writeValueAsString(value).removeSurrounding("\"")

        contentType.contains("x-www-form-urlencoded", ignoreCase = true) ->
            URLEncoder.encode(value, StandardCharsets.UTF_8)

        else -> value
    }

    private fun NotificationParameters.valueOf(placeholder: String) = when (placeholder) {
        "title" -> title
        "message" -> message
        "priority" -> priority.toString()
        "clickUrl" -> clickUrl
        "eventType" -> eventType
        "monitorType" -> monitorType.orEmpty()
        "timestamp" -> timestamp.toString()
        "serverName" -> serverName
        "serverId" -> serverId
        else -> null
    }

    companion object {
        private val PLACEHOLDER = Regex("\\{\\{\\s*(\\w+)\\s*}}")
    }
}
