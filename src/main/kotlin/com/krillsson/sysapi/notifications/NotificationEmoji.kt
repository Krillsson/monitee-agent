package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.core.monitoring.Monitor

object NotificationEmoji {

    fun decorate(notification: NotificationParameters): NotificationParameters {
        val messageEmoji = notification.monitorType?.emoji()
        return notification.copy(
            title = "${notification.eventType.emoji()} ${notification.title}",
            message = if (messageEmoji == null) notification.message else "$messageEmoji ${notification.message}"
        )
    }

    private fun NotificationEventType.emoji() = when (this) {
        NotificationEventType.ONGOING_EVENT -> "🚨"
        NotificationEventType.RESOLVED_EVENT -> "✅"
        NotificationEventType.UPDATE_AVAILABLE -> "🆕"
        NotificationEventType.MONITORED_ITEM_MISSING -> "❓"
        NotificationEventType.CONTAINER_IMAGE_UPDATE_AVAILABLE -> "🐳"
        NotificationEventType.CONTAINER_IMAGE_UPDATE_DIGEST -> "🐳"
    }

    private fun Monitor.Type.emoji() = when (this) {
        Monitor.Type.CPU_LOAD,
        Monitor.Type.CONTAINER_CPU_LOAD,
        Monitor.Type.PROCESS_CPU_LOAD -> "🖥️"

        Monitor.Type.LOAD_AVERAGE_ONE_MINUTE,
        Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES,
        Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "📈"

        Monitor.Type.CPU_TEMP,
        Monitor.Type.DISK_TEMPERATURE,
        Monitor.Type.GPU_TEMPERATURE -> "🌡️"

        Monitor.Type.FILE_SYSTEM_SPACE -> "💾"

        Monitor.Type.DISK_READ_RATE,
        Monitor.Type.DISK_WRITE_RATE -> "💽"

        Monitor.Type.DISK_SMART_HEALTH -> "🩺"

        Monitor.Type.MEMORY_SPACE,
        Monitor.Type.MEMORY_USED,
        Monitor.Type.CONTAINER_MEMORY_SPACE,
        Monitor.Type.PROCESS_MEMORY_SPACE -> "🧠"

        Monitor.Type.GPU_VRAM_USAGE,
        Monitor.Type.GPU_UTILIZATION -> "🎮"

        Monitor.Type.NETWORK_UP,
        Monitor.Type.NETWORK_UPLOAD_RATE,
        Monitor.Type.NETWORK_DOWNLOAD_RATE -> "🌐"

        Monitor.Type.CONTAINER_RUNNING,
        Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "🐳"

        Monitor.Type.PROCESS_EXISTS -> "⚙️"

        Monitor.Type.CONNECTIVITY -> "📡"

        Monitor.Type.WEBSERVER_UP -> "🌍"

        Monitor.Type.EXTERNAL_IP_CHANGED -> "📍"

        Monitor.Type.UPS_OPERATING_NORMALLY,
        Monitor.Type.UPS_LOAD_PERCENTAGE,
        Monitor.Type.UPS_LOAD_WATT -> "🔋"

        else -> "📊"
    }
}
