package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.notifications.Notification
import org.springframework.stereotype.Component

@Component
class OngoingEventFormatter(
    val temperatureFormatter: TemperatureFormatter,
    val byteFormatter: ByteFormatter,
    val durationFormatter: DurationFormatter,
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
                Monitor.Type.NETWORK_UP -> "Network down $serverName"
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
            }
        }

    }

    fun formatOngoingEventDescription(
        notification: Notification.OngoingEvent
    ): String {
        return with(notification) {
            val formattedThreshold = threshold.format(monitorType)
            val formattedValue = value.format(monitorType)
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
            }
        }

    }

    fun MonitoredValue.format(
        type: Monitor.Type,
    ): String {
        return when (type) {
            Monitor.Type.CPU_LOAD -> formatPercent(
                (this as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.CPU_TEMP -> temperatureFormatter.format((this as MonitoredValue.NumericalValue).value.toInt())
            Monitor.Type.MEMORY_SPACE -> byteFormatter.format((this as MonitoredValue.NumericalValue).value)
            Monitor.Type.NETWORK_UP -> if ((this as MonitoredValue.ConditionalValue).value) "up" else "down"
            Monitor.Type.NETWORK_UPLOAD_RATE -> byteFormatter.formatNetworkRate((this as MonitoredValue.NumericalValue).value)
            Monitor.Type.NETWORK_DOWNLOAD_RATE -> byteFormatter.formatNetworkRate((this as MonitoredValue.NumericalValue).value)
            Monitor.Type.CONTAINER_RUNNING -> if ((this as MonitoredValue.ConditionalValue).value) "running" else "stopped"
            Monitor.Type.PROCESS_MEMORY_SPACE -> byteFormatter.format((this as MonitoredValue.NumericalValue).value)
            Monitor.Type.PROCESS_CPU_LOAD -> formatPercent(
                (this as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.PROCESS_EXISTS -> if ((this as MonitoredValue.ConditionalValue).value) "exists" else "dead"
            Monitor.Type.CONNECTIVITY -> if ((this as MonitoredValue.ConditionalValue).value) "connected" else "disconnected"
            Monitor.Type.EXTERNAL_IP_CHANGED -> if ((this as MonitoredValue.ConditionalValue).value) "unchanged" else "changed"
            Monitor.Type.FILE_SYSTEM_SPACE -> byteFormatter.format((this as MonitoredValue.NumericalValue).value)
            Monitor.Type.DISK_READ_RATE -> byteFormatter.format((this as MonitoredValue.NumericalValue).value) + "/s"
            Monitor.Type.DISK_WRITE_RATE -> byteFormatter.format((this as MonitoredValue.NumericalValue).value) + "/s"
            Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> String.format(
                "%.2f",
                (this as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> String.format(
                "%.2f",
                (this as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> String.format(
                "%.2f",
                (this as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.MEMORY_USED -> byteFormatter.format((this as MonitoredValue.NumericalValue).value)
            Monitor.Type.CONTAINER_MEMORY_SPACE -> byteFormatter.format((this as MonitoredValue.NumericalValue).value)
            Monitor.Type.CONTAINER_CPU_LOAD -> formatPercent(
                (this as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.WEBSERVER_UP -> if ((this as MonitoredValue.ConditionalValue).value) "up" else "down"
            Monitor.Type.DISK_TEMPERATURE -> temperatureFormatter.format((this as MonitoredValue.NumericalValue).value.toInt())
        }
    }

    private fun formatPercent(percent: Float): String {
        return String.format("%.0f%%", percent)
    }
}