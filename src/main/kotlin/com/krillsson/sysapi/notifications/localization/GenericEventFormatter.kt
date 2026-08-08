package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.util.EnvironmentUtils
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class GenericEventFormatter {

    companion object {
        private const val MAX_NAMES_IN_DIGEST = 5
    }

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

    fun formatContainerImageUpdateTitle(serverName: String): String {
        return "New container image available on $serverName"
    }

    fun formatContainerImageUpdateDescription(notification: Notification.GenericEvent.ContainerImageUpdateAvailable): String {
        return with(notification) {
            "${containerName.removePrefix("/")} is running an outdated $imageRef"
        }
    }

    fun formatContainerImageUpdateDigestTitle(serverName: String): String {
        return "Container updates available on $serverName"
    }

    fun formatContainerImageUpdateDigestDescription(notification: Notification.GenericEvent.ContainerImageUpdateDigest): String {
        val names = notification.containerNames.sorted()
        if (names.size == 1) {
            return "${names.single()} is running an outdated image"
        }
        val listed = names.take(MAX_NAMES_IN_DIGEST).joinToString()
        val remaining = names.size - MAX_NAMES_IN_DIGEST
        return if (remaining > 0) {
            "${names.size} containers have updates: $listed and $remaining more"
        } else {
            "${names.size} containers have updates: $listed"
        }
    }

    fun formatPackageUpdatesTitle(serverName: String): String {
        return "Package updates available on $serverName"
    }

    fun formatPackageUpdatesDescription(notification: Notification.GenericEvent.PackageUpdatesAvailable): String {
        return with(notification) {
            val packages = if (totalCount == 1) "1 package" else "$totalCount packages"
            if (securityCount != null && securityCount > 0) {
                "$packages can be updated with $manager, $securityCount of them security"
            } else {
                "$packages can be updated with $manager"
            }
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
            Monitor.Type.UPS_LOAD_WATT -> "UPS — Power usage (W)"
            Monitor.Type.GPU_VRAM_USAGE -> "GPU — VRAM usage"
            Monitor.Type.GPU_TEMPERATURE -> "GPU — Temperature"
            Monitor.Type.GPU_UTILIZATION -> "GPU — Load percent"
            Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "Docker — Container image up to date"
            Monitor.Type.PACKAGE_UPDATES -> "System — Pending updates"
            Monitor.Type.PACKAGE_SECURITY_UPDATES -> "System — Pending security updates"
        }
    }
}