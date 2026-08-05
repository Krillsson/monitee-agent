package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.notifications.Notification
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ResolvedEventFormatter(
    private val monitoredValueFormatter: MonitoredValueFormatter,
    private val durationFormatter: DurationFormatter,
) {
    fun formatResolvedEventTitle(
        notification: Notification.ResolvedEvent,
        serverName: String,
    ): String {
        return with(notification) {
            when (monitorType) {
                Monitor.Type.CPU_LOAD -> "CPU load back to normal on $serverName"
                Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "Load avg. 1m back to normal on $serverName"
                Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "Load avg. 5m back to normal on $serverName"
                Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "Load avg. 15m back to normal on $serverName"
                Monitor.Type.CPU_TEMP -> "CPU temp. back to normal on $serverName"
                Monitor.Type.FILE_SYSTEM_SPACE -> "Space back to normal on $serverName"
                Monitor.Type.DISK_READ_RATE -> "Disk read rate back to normal on $serverName"
                Monitor.Type.DISK_TEMPERATURE -> "Disk temp. back to normal on $serverName"
                Monitor.Type.DISK_WRITE_RATE -> "Disk write rate back to normal on $serverName"
                Monitor.Type.MEMORY_SPACE -> "Memory space back to normal on $serverName"
                Monitor.Type.MEMORY_USED -> "Memory usage back to normal on $serverName"
                Monitor.Type.NETWORK_UP -> "Network back up on $serverName"
                Monitor.Type.NETWORK_UPLOAD_RATE -> "Upload back to normal on $serverName"
                Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Download back to normal on $serverName"
                Monitor.Type.CONTAINER_RUNNING -> "Container running again on $serverName"
                Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container memory usage back to normal on $serverName"
                Monitor.Type.CONTAINER_CPU_LOAD -> "Container CPU load back to normal on $serverName"
                Monitor.Type.PROCESS_MEMORY_SPACE -> "Process memory usage back to normal on $serverName"
                Monitor.Type.PROCESS_CPU_LOAD -> "Process CPU usage back to normal on $serverName"
                Monitor.Type.PROCESS_EXISTS -> "Process running again on $serverName"
                Monitor.Type.CONNECTIVITY -> "Connection is back on $serverName"
                Monitor.Type.WEBSERVER_UP -> "Webserver is back up on $serverName"
                Monitor.Type.EXTERNAL_IP_CHANGED -> "External IP back to normal on $serverName"
                Monitor.Type.UPS_LOAD_PERCENTAGE -> "Load back to normal on UPS $serverName"
                Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS operating normally again on $serverName"
                Monitor.Type.DISK_SMART_HEALTH -> "Disk healthy again on $serverName"
                Monitor.Type.UPS_LOAD_WATT -> "Power usage back to normal on $serverName"
                Monitor.Type.GPU_VRAM_USAGE -> "GPU VRAM usage back to normal on $serverName"
                Monitor.Type.GPU_TEMPERATURE -> "GPU temp. back to normal on $serverName"
                Monitor.Type.GPU_UTILIZATION -> "GPU load back to normal on $serverName"
                Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "Container image up to date on $serverName"
                Monitor.Type.PACKAGE_UPDATES -> "Pending updates back to normal on $serverName"
                Monitor.Type.PACKAGE_SECURITY_UPDATES -> "Pending security updates back to normal on $serverName"
            }
        }
    }

    fun formatResolvedEventDescription(
        notification: Notification.ResolvedEvent
    ): String {
        return with(notification) {
            val formattedThreshold = monitoredValueFormatter.format(threshold, monitorType)
            val formattedValue = monitoredValueFormatter.format(endValue, monitorType)
            val formattedDuration = durationFormatter.formatDuration(
                Duration.between(startTime, endTime).seconds
            )
            return when (monitorType) {
                Monitor.Type.CPU_LOAD -> "Load is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.CPU_TEMP -> "Temperature is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.MEMORY_SPACE -> "Memory is back above $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.NETWORK_UP -> "NIC $monitoredItemId is $formattedValue again after $formattedDuration"
                Monitor.Type.NETWORK_UPLOAD_RATE -> "Upload rate on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Download rate on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.CONTAINER_RUNNING -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } is $formattedValue again after $formattedDuration"

                Monitor.Type.PROCESS_MEMORY_SPACE -> "Process memory on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.PROCESS_CPU_LOAD -> "Process CPU usage on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.PROCESS_EXISTS -> "Process $monitoredItemId is back after $formattedDuration"
                Monitor.Type.CONNECTIVITY -> "Host machine is $formattedValue again after $formattedDuration"
                Monitor.Type.EXTERNAL_IP_CHANGED -> "External IP is $formattedValue after $formattedDuration"
                Monitor.Type.FILE_SYSTEM_SPACE -> "Space on $monitoredItemId is back above $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.DISK_READ_RATE -> "Read rate on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.DISK_WRITE_RATE -> "Write rate on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.DISK_SMART_HEALTH -> "Health on $monitoredItemId is back to $formattedValue after $formattedDuration"
                Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "1m load average is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "5m load average is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "15m load average is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.MEMORY_USED -> "Memory usage is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } memory usage is back below $formattedThreshold at $formattedValue after $formattedDuration"

                Monitor.Type.CONTAINER_CPU_LOAD -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } CPU usage is back below $formattedThreshold at $formattedValue after $formattedDuration"

                Monitor.Type.WEBSERVER_UP -> "$monitoredItemId is responding with 200/OK again after $formattedDuration"
                Monitor.Type.DISK_TEMPERATURE -> "Temperature on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS $monitoredItemId is operating normally again after $formattedDuration"
                Monitor.Type.UPS_LOAD_PERCENTAGE -> "UPS $monitoredItemId load is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.UPS_LOAD_WATT -> "UPS $monitoredItemId load is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.GPU_VRAM_USAGE -> "VRAM usage on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.GPU_TEMPERATURE -> "Temperature on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.GPU_UTILIZATION -> "Load on $monitoredItemId is back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "Container ${
                    monitoredItemId?.replace(
                        "/",
                        ""
                    )
                } is running an $formattedValue image after $formattedDuration"

                Monitor.Type.PACKAGE_UPDATES -> "Pending updates are back below $formattedThreshold at $formattedValue after $formattedDuration"
                Monitor.Type.PACKAGE_SECURITY_UPDATES -> "Pending security updates are back below $formattedThreshold at $formattedValue after $formattedDuration"
            }
        }
    }
}
