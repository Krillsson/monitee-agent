package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.core.monitoring.Monitor

object NotificationEmoji {

    private data class Emoji(val character: String, val ntfyTag: String)

    fun decorate(notification: NotificationParameters): NotificationParameters {
        val messageEmoji = notification.monitorType?.emoji()
        return notification.copy(
            title = "${notification.eventType.emoji().character} ${notification.title}",
            message = if (messageEmoji == null) {
                notification.message
            } else {
                "${messageEmoji.character} ${notification.message}"
            }
        )
    }

    fun ntfyTags(notification: NotificationParameters): List<String> {
        return listOfNotNull(
            notification.eventType.emoji().ntfyTag,
            notification.monitorType?.emoji()?.ntfyTag
        )
    }

    private fun NotificationEventType.emoji() = when (this) {
        NotificationEventType.ONGOING_EVENT -> Emoji("🚨", "rotating_light")
        NotificationEventType.RESOLVED_EVENT -> Emoji("✅", "white_check_mark")
        NotificationEventType.UPDATE_AVAILABLE -> Emoji("🆕", "new")
        NotificationEventType.MONITORED_ITEM_MISSING -> Emoji("❓", "question")
        NotificationEventType.CONTAINER_IMAGE_UPDATE_AVAILABLE -> Emoji("🐳", "whale")
        NotificationEventType.CONTAINER_IMAGE_UPDATE_DIGEST -> Emoji("🐳", "whale")
    }

    private fun Monitor.Type.emoji() = when (this) {
        Monitor.Type.CPU_LOAD,
        Monitor.Type.CONTAINER_CPU_LOAD,
        Monitor.Type.PROCESS_CPU_LOAD -> Emoji("🖥️", "computer")

        Monitor.Type.LOAD_AVERAGE_ONE_MINUTE,
        Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES,
        Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> Emoji("📈", "chart_with_upwards_trend")

        Monitor.Type.CPU_TEMP,
        Monitor.Type.DISK_TEMPERATURE,
        Monitor.Type.GPU_TEMPERATURE -> Emoji("🌡️", "thermometer")

        Monitor.Type.FILE_SYSTEM_SPACE -> Emoji("💾", "floppy_disk")

        Monitor.Type.DISK_READ_RATE,
        Monitor.Type.DISK_WRITE_RATE -> Emoji("💽", "minidisc")

        Monitor.Type.DISK_SMART_HEALTH -> Emoji("🩺", "stethoscope")

        Monitor.Type.MEMORY_SPACE,
        Monitor.Type.MEMORY_USED,
        Monitor.Type.CONTAINER_MEMORY_SPACE,
        Monitor.Type.PROCESS_MEMORY_SPACE -> Emoji("🧠", "brain")

        Monitor.Type.GPU_VRAM_USAGE,
        Monitor.Type.GPU_UTILIZATION -> Emoji("🎮", "video_game")

        Monitor.Type.NETWORK_UP,
        Monitor.Type.NETWORK_UPLOAD_RATE,
        Monitor.Type.NETWORK_DOWNLOAD_RATE -> Emoji("🌐", "globe_with_meridians")

        Monitor.Type.CONTAINER_RUNNING,
        Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> Emoji("🐳", "whale")

        Monitor.Type.PROCESS_EXISTS -> Emoji("⚙️", "gear")

        Monitor.Type.CONNECTIVITY -> Emoji("📡", "satellite_antenna")

        Monitor.Type.WEBSERVER_UP -> Emoji("🌍", "earth_africa")

        Monitor.Type.EXTERNAL_IP_CHANGED -> Emoji("📍", "round_pushpin")

        Monitor.Type.UPS_OPERATING_NORMALLY,
        Monitor.Type.UPS_LOAD_PERCENTAGE,
        Monitor.Type.UPS_LOAD_WATT -> Emoji("🔋", "battery")

        else -> Emoji("📊", "bar_chart")
    }
}
