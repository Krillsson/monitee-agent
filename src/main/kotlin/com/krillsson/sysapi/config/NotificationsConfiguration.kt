package com.krillsson.sysapi.config

data class NotificationsConfiguration(
    val serverName: String? = null,
    val ntfy: NtfyConfiguration = NtfyConfiguration(),
    val webhooks: List<WebhookConfiguration> = emptyList()
) {
    data class NtfyConfiguration(
        val enabled: Boolean = false,
        val url: String = "https://ntfy.sh",
        val topic: String? = null,
        val token: String? = null,
        val username: String? = null,
        val password: String? = null
    )

    data class WebhookConfiguration(
        val name: String? = null,
        val enabled: Boolean = true,
        val url: String = "",
        val method: String = "POST",
        val contentType: String = "application/json",
        val headers: Map<String, String> = emptyMap(),
        val username: String? = null,
        val password: String? = null,
        val body: String? = null,
        val timeoutSeconds: Long = 10
    )
}
