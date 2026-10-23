package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.notifications.Notification
import org.springframework.stereotype.Component

@Component
class OngoingEventFormatter(
    private val monitoredValueFormatter: MonitoredValueFormatter,
    private val checkEventFormatter: CheckEventFormatter,
) {
    fun formatOngoingEventTitle(
        notification: Notification.OngoingEvent,
        serverName: String,
    ): String {
        return with(notification) {
            val level = when {
                severity == EventSeverity.CRITICAL -> "critical"
                monitorType.alertsWhenBelow() -> "low"
                else -> "high"
            }
            when (monitorType) {
                Monitor.Type.CPU_LOAD -> "CPU load $level on $serverName"
                Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "Load avg. 1m $level on $serverName"
                Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "Load avg. 5m $level on $serverName"
                Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "Load avg. 15m $level on $serverName"
                Monitor.Type.CPU_TEMP -> "CPU temp. $level on $serverName"
                Monitor.Type.FILE_SYSTEM_SPACE -> "Space $level on $serverName"
                Monitor.Type.DISK_READ_RATE -> "Disk read rate $level on $serverName"
                Monitor.Type.DISK_TEMPERATURE -> "Disk temp. $level on $serverName"
                Monitor.Type.DISK_WRITE_RATE -> "Disk write rate $level on $serverName"
                Monitor.Type.MEMORY_SPACE -> "Memory space $level on $serverName"
                Monitor.Type.MEMORY_USED -> "Memory usage $level on $serverName"
                Monitor.Type.NETWORK_UP -> "Network down on $serverName"
                Monitor.Type.NETWORK_UPLOAD_RATE -> "Upload $level on $serverName"
                Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Download $level on $serverName"
                Monitor.Type.CONTAINER_RUNNING -> "Container stopped on $serverName"
                Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container memory usage $level on $serverName"
                Monitor.Type.CONTAINER_CPU_LOAD -> "Container CPU load $level on $serverName"
                Monitor.Type.PROCESS_MEMORY_SPACE -> "Process memory usage $level on $serverName"
                Monitor.Type.PROCESS_CPU_LOAD -> "Process CPU usage $level on $serverName"
                Monitor.Type.PROCESS_EXISTS -> "Process stopped on $serverName"
                Monitor.Type.CONNECTIVITY -> "Connection is down on $serverName"
                Monitor.Type.WEBSERVER_UP -> "Check is failing on $serverName"
                Monitor.Type.CHECK_LATENCY -> "Check latency $level on $serverName"
                Monitor.Type.CHECK_CERTIFICATE_EXPIRY -> "Certificate expiring on $serverName"
                Monitor.Type.EXTERNAL_IP_CHANGED -> "External IP changed on $serverName"
                Monitor.Type.UPS_LOAD_PERCENTAGE -> "UPS load $level on $serverName"
                Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS not operating normally on $serverName"
                Monitor.Type.DISK_SMART_HEALTH -> "Disk unhealthy on $serverName"
                Monitor.Type.UPS_LOAD_WATT -> "Power usage $level on $serverName"
                Monitor.Type.GPU_VRAM_USAGE -> "GPU VRAM usage $level on $serverName"
                Monitor.Type.GPU_TEMPERATURE -> "GPU temp. $level on $serverName"
                Monitor.Type.GPU_UTILIZATION -> "GPU load $level on $serverName"
                Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "New container image available on $serverName"
            }
        }

    }

    fun formatOngoingEventDescription(
        notification: Notification.OngoingEvent
    ): String {
        return with(notification) {
            val crossedWarningThreshold = warningThreshold.takeIf { severity == EventSeverity.WARNING }
            val formattedThreshold = monitoredValueFormatter.format(crossedWarningThreshold ?: threshold, monitorType)
            val formattedValue = monitoredValueFormatter.format(value, monitorType)
            val criticalAt = if (crossedWarningThreshold == null) {
                ""
            } else {
                ", critical at ${monitoredValueFormatter.format(threshold, monitorType)}"
            }
            val wentAbove = "went above $formattedThreshold to $formattedValue$criticalAt"
            val wentBelow = "went below $formattedThreshold to $formattedValue$criticalAt"
            return when (monitorType) {
                Monitor.Type.CPU_LOAD -> "Load $wentAbove"
                Monitor.Type.CPU_TEMP -> "Temperature $wentAbove"
                Monitor.Type.MEMORY_SPACE -> "Memory $wentBelow"
                Monitor.Type.NETWORK_UP -> "NIC $monitoredItemId went $formattedThreshold to $formattedValue"
                Monitor.Type.NETWORK_UPLOAD_RATE -> "Upload rate on $monitoredItemId $wentAbove"
                Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Download rate on $monitoredItemId $wentAbove"
                Monitor.Type.CONTAINER_RUNNING -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } is $formattedValue"

                Monitor.Type.PROCESS_MEMORY_SPACE -> "Process memory on $monitoredItemId $wentAbove"
                Monitor.Type.PROCESS_CPU_LOAD -> "Process CPU usage on $monitoredItemId $wentAbove"
                Monitor.Type.PROCESS_EXISTS -> "Process $monitoredItemId is $formattedValue"
                Monitor.Type.CONNECTIVITY -> "Host machine went from is $formattedValue"
                Monitor.Type.EXTERNAL_IP_CHANGED -> "External IP $formattedValue"
                Monitor.Type.FILE_SYSTEM_SPACE -> "Space on $monitoredItemId $wentBelow"
                Monitor.Type.DISK_READ_RATE -> "Read rate on $monitoredItemId $wentAbove"
                Monitor.Type.DISK_WRITE_RATE -> "Write rate on $monitoredItemId $wentAbove"
                Monitor.Type.DISK_SMART_HEALTH -> "Health on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "1m load average $wentAbove"
                Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "5m load average $wentAbove"
                Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "15m load average $wentAbove"
                Monitor.Type.MEMORY_USED -> "Memory usage $wentAbove"
                Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } memory usage $wentAbove"

                Monitor.Type.CONTAINER_CPU_LOAD -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } CPU usage $wentAbove"

                Monitor.Type.WEBSERVER_UP -> checkEventFormatter.formatWebServerUpOngoingDescription(monitoredItemId)
                Monitor.Type.CHECK_LATENCY -> checkEventFormatter.formatCheckLatencyOngoingDescription(
                    monitoredItemId,
                    formattedValue,
                    formattedThreshold
                ) + criticalAt
                Monitor.Type.CHECK_CERTIFICATE_EXPIRY -> checkEventFormatter.formatCertificateExpiryOngoingDescription(
                    monitoredItemId,
                    (value as MonitoredValue.NumericalValue).value,
                    formattedThreshold
                ) + criticalAt
                Monitor.Type.DISK_TEMPERATURE -> "Temperature on $monitoredItemId $wentAbove"
                Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS $monitoredItemId is not operating normally"
                Monitor.Type.UPS_LOAD_PERCENTAGE -> "UPS $monitoredItemId load $wentAbove"
                Monitor.Type.UPS_LOAD_WATT -> "UPS $monitoredItemId load $wentAbove"
                Monitor.Type.GPU_VRAM_USAGE -> "VRAM usage on $monitoredItemId $wentAbove"
                Monitor.Type.GPU_TEMPERATURE -> "Temperature on $monitoredItemId $wentAbove"
                Monitor.Type.GPU_UTILIZATION -> "Load on $monitoredItemId $wentAbove"
                Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } is running an $formattedValue image"
            }
        }

    }

    private fun Monitor.Type.alertsWhenBelow() =
        this == Monitor.Type.FILE_SYSTEM_SPACE || this == Monitor.Type.MEMORY_SPACE
}
