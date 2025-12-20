package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.util.EnvironmentUtils
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class GenericEventFormatter {

    fun formatUpdateEventTitle(serverName: String): String {
        return "New monitee-agent version available for ${EnvironmentUtils.hostName}"
    }

    fun formatUpdateEventDescription(notification: Notification.GenericEvent.UpdateAvailable): String {
        return with(notification) {
            val date = OffsetDateTime.parse(publishDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val formattedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date)
            "$newVersion published at $formattedDate. Server is running $currentVersion"
        }

    }

    fun formatMonitoredItemMissingTitle(event: Notification.GenericEvent.MonitoredItemMissing, serverName: String): String {
        return "Monitored item missing from ${EnvironmentUtils.hostName}"
    }

    fun formatMonitoredItemMissingDescription(event: Notification.GenericEvent.MonitoredItemMissing): String {
        return "${event.monitorType.asDescription()} monitor's item ${event.monitoredItemId} is no longer present in the system"
    }

    private fun Monitor.Type.asDescription(): String {
        return when (this) {
            Monitor.Type.CPU_TEMP -> "CPU — Temperature"
            Monitor.Type.CPU_LOAD -> "CPU — Load percent"
            Monitor.Type.FILE_SYSTEM_SPACE -> "File system — Free space"
            Monitor.Type.MEMORY_SPACE -> "Memory — Free space"
            Monitor.Type.NETWORK_UP -> "Network — Up"
            Monitor.Type.CONTAINER_RUNNING -> "Docker — Container running"
            Monitor.Type.PROCESS_EXISTS -> "Process — Exists"
            Monitor.Type.DISK_READ_RATE -> "Drive — Read rate"
            Monitor.Type.DISK_WRITE_RATE -> "Drive — Write rate"
            Monitor.Type.NETWORK_UPLOAD_RATE -> "Network — Upload rate"
            Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Network — Download rate"
            Monitor.Type.PROCESS_MEMORY_SPACE -> "Process — Memory usage"
            Monitor.Type.CONNECTIVITY -> "Host — Connectivity"
            Monitor.Type.EXTERNAL_IP_CHANGED -> "Host — External IP changed"
            Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "Load average — 1m"
            Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "Load average — 5m"
            Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "Load average — 15m"
            Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container — Memory usage"
            Monitor.Type.CONTAINER_CPU_LOAD -> "Container — CPU usage"
            Monitor.Type.WEBSERVER_UP -> "Webserver — Replies 200/OK"
            Monitor.Type.DISK_TEMPERATURE -> "Drive — Temperature"
            Monitor.Type.DISK_SMART_HEALTH -> "Drive — S.M.A.R.T health"
            Monitor.Type.MEMORY_USED -> "Memory — Usage"
            Monitor.Type.PROCESS_CPU_LOAD -> "Process — CPU usage"
            Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS — Operating normally"
            Monitor.Type.UPS_LOAD_PERCENTAGE -> "UPS — Load percent"
        }
    }
}