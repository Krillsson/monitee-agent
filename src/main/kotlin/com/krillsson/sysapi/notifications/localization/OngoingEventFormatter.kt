package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.notifications.Notification
import org.springframework.stereotype.Component

@Component
class OngoingEventFormatter(
    private val monitoredValueFormatter: MonitoredValueFormatter,
) {
    fun formatOngoingEventTitle(
        notification: Notification.OngoingEvent,
        serverName: String,
    ): String {
        return with(notification) {
            when (monitorType) {
                Monitor.Type.CPU_LOAD -> "CPU load too high on $serverName"
                Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "Load avg. 1m too high on $serverName"
                Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "Load avg. 5m too high on $serverName"
                Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "Load avg. 15m too high on $serverName"
                Monitor.Type.CPU_TEMP -> "CPU temp. too high on $serverName"
                Monitor.Type.FILE_SYSTEM_SPACE -> "Space too low on $serverName"
                Monitor.Type.DISK_READ_RATE -> "Disk read rate too high $serverName"
                Monitor.Type.DISK_TEMPERATURE -> "Disk temp. too high on $serverName"
                Monitor.Type.DISK_WRITE_RATE -> "Disk write rate too high on $serverName"
                Monitor.Type.MEMORY_SPACE -> "Memory space too low on $serverName"
                Monitor.Type.MEMORY_USED -> "Memory usage too high on $serverName"
                Monitor.Type.NETWORK_UP -> "Network down on $serverName"
                Monitor.Type.NETWORK_UPLOAD_RATE -> "Upload too high on $serverName"
                Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Download too high on $serverName"
                Monitor.Type.CONTAINER_RUNNING -> "Container stopped on $serverName"
                Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container memory usage too high on $serverName"
                Monitor.Type.CONTAINER_CPU_LOAD -> "Container CPU load too high on $serverName"
                Monitor.Type.PROCESS_MEMORY_SPACE -> "Process memory usage too high on $serverName"
                Monitor.Type.PROCESS_CPU_LOAD -> "Process CPU usage too high on $serverName"
                Monitor.Type.PROCESS_EXISTS -> "Process stopped on $serverName"
                Monitor.Type.CONNECTIVITY -> "Connection is down on $serverName"
                Monitor.Type.WEBSERVER_UP -> "Webserver is down on $serverName"
                Monitor.Type.EXTERNAL_IP_CHANGED -> "External IP changed on $serverName"
                Monitor.Type.UPS_LOAD_PERCENTAGE -> "Load too high on UPS $serverName"
                Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS not operating normally on $serverName"
                Monitor.Type.DISK_SMART_HEALTH -> "Disk unhealthy on $serverName"
                Monitor.Type.UPS_LOAD_WATT -> "Power usage too high on $serverName"
                Monitor.Type.GPU_VRAM_USAGE -> "GPU VRAM usage too high on $serverName"
                Monitor.Type.GPU_TEMPERATURE -> "GPU temp. too high on $serverName"
                Monitor.Type.GPU_UTILIZATION -> "GPU load too high on $serverName"
                Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "New container image available on $serverName"
                Monitor.Type.PACKAGE_UPDATES -> "Package updates available on $serverName"
                Monitor.Type.PACKAGE_SECURITY_UPDATES -> "Security updates available on $serverName"
            }
        }

    }

    fun formatOngoingEventDescription(
        notification: Notification.OngoingEvent
    ): String {
        return with(notification) {
            val formattedThreshold = monitoredValueFormatter.format(threshold, monitorType)
            val formattedValue = monitoredValueFormatter.format(value, monitorType)
            return when (monitorType) {
                Monitor.Type.CPU_LOAD -> "Load went above $formattedThreshold to $formattedValue"
                Monitor.Type.CPU_TEMP -> "Temperature went above $formattedThreshold to $formattedValue"
                Monitor.Type.MEMORY_SPACE -> "Memory went below $formattedThreshold to $formattedValue"
                Monitor.Type.NETWORK_UP -> "NIC $monitoredItemId went $formattedThreshold to $formattedValue"
                Monitor.Type.NETWORK_UPLOAD_RATE -> "Upload rate on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Download rate on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.CONTAINER_RUNNING -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } is $formattedValue"

                Monitor.Type.PROCESS_MEMORY_SPACE -> "Process memory on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.PROCESS_CPU_LOAD -> "Process CPU usage on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.PROCESS_EXISTS -> "Process $monitoredItemId is $formattedValue"
                Monitor.Type.CONNECTIVITY -> "Host machine went from is $formattedValue"
                Monitor.Type.EXTERNAL_IP_CHANGED -> "External IP $formattedValue"
                Monitor.Type.FILE_SYSTEM_SPACE -> "Space on $monitoredItemId went below $formattedThreshold to $formattedValue"
                Monitor.Type.DISK_READ_RATE -> "Read rate on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.DISK_WRITE_RATE -> "Write rate on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.DISK_SMART_HEALTH -> "Health on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "1m load average went above $formattedThreshold to $formattedValue"
                Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "5m load average went above $formattedThreshold to $formattedValue"
                Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "15m load average went above $formattedThreshold to $formattedValue"
                Monitor.Type.MEMORY_USED -> "Memory usage went above $formattedThreshold to $formattedValue"
                Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } memory usage went above $formattedThreshold to $formattedValue"

                Monitor.Type.CONTAINER_CPU_LOAD -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } CPU usage went above $formattedThreshold to $formattedValue"

                Monitor.Type.WEBSERVER_UP -> "$monitoredItemId is not responding with 200/OK"
                Monitor.Type.DISK_TEMPERATURE -> "Temperature on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS $monitoredItemId is not operating normally"
                Monitor.Type.UPS_LOAD_PERCENTAGE -> "UPS $monitoredItemId load went above $formattedThreshold to $formattedValue"
                Monitor.Type.UPS_LOAD_WATT -> "UPS $monitoredItemId load went above $formattedThreshold to $formattedValue"
                Monitor.Type.GPU_VRAM_USAGE -> "VRAM usage on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.GPU_TEMPERATURE -> "Temperature on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.GPU_UTILIZATION -> "Load on $monitoredItemId went above $formattedThreshold to $formattedValue"
                Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } is running an $formattedValue image"

                Monitor.Type.PACKAGE_UPDATES -> "Pending updates went above $formattedThreshold to $formattedValue"
                Monitor.Type.PACKAGE_SECURITY_UPDATES -> "Pending security updates went above $formattedThreshold to $formattedValue"
            }
        }

    }
}
