package com.krillsson.sysapi.config

data class NotificationsConfiguration(
    val ntfy: NtfyConfiguration = NtfyConfiguration()
) {
    data class NtfyConfiguration(
        val enabled: Boolean = false,
        val url: String = "https://ntfy.sh",
        val topic: String? = null
    )
}