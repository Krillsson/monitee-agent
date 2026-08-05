package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.smart.HealthStatus
import org.springframework.stereotype.Component

@Component
class MonitoredValueFormatter(
    private val temperatureFormatter: TemperatureFormatter,
    private val byteFormatter: ByteFormatter,
) {
    fun format(value: MonitoredValue, type: Monitor.Type): String {
        return when (type) {
            Monitor.Type.CPU_LOAD -> formatPercent(
                (value as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.CPU_TEMP -> temperatureFormatter.format((value as MonitoredValue.NumericalValue).value.toInt())
            Monitor.Type.MEMORY_SPACE -> byteFormatter.format((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.NETWORK_UP -> if ((value as MonitoredValue.ConditionalValue).value) "up" else "down"
            Monitor.Type.NETWORK_UPLOAD_RATE -> byteFormatter.formatNetworkRate((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.NETWORK_DOWNLOAD_RATE -> byteFormatter.formatNetworkRate((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.CONTAINER_RUNNING -> if ((value as MonitoredValue.ConditionalValue).value) "running" else "stopped"
            Monitor.Type.PROCESS_MEMORY_SPACE -> byteFormatter.format((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.PROCESS_CPU_LOAD -> formatPercent(
                (value as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.PROCESS_EXISTS -> if ((value as MonitoredValue.ConditionalValue).value) "exists" else "dead"
            Monitor.Type.CONNECTIVITY -> if ((value as MonitoredValue.ConditionalValue).value) "connected" else "disconnected"
            Monitor.Type.EXTERNAL_IP_CHANGED -> if ((value as MonitoredValue.ConditionalValue).value) "unchanged" else "changed"
            Monitor.Type.FILE_SYSTEM_SPACE -> byteFormatter.format((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.DISK_READ_RATE -> byteFormatter.format((value as MonitoredValue.NumericalValue).value) + "/s"
            Monitor.Type.DISK_WRITE_RATE -> byteFormatter.format((value as MonitoredValue.NumericalValue).value) + "/s"
            Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> String.format(
                "%.2f",
                (value as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> String.format(
                "%.2f",
                (value as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> String.format(
                "%.2f",
                (value as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.MEMORY_USED -> byteFormatter.format((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.CONTAINER_MEMORY_SPACE -> byteFormatter.format((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.CONTAINER_CPU_LOAD -> formatPercent(
                (value as MonitoredValue.FractionalValue).value
            )

            Monitor.Type.WEBSERVER_UP -> if ((value as MonitoredValue.ConditionalValue).value) "up" else "down"
            Monitor.Type.DISK_TEMPERATURE -> temperatureFormatter.format((value as MonitoredValue.NumericalValue).value.toInt())
            Monitor.Type.UPS_OPERATING_NORMALLY -> if ((value as MonitoredValue.ConditionalValue).value) "operating normally" else "not operating normally"
            Monitor.Type.UPS_LOAD_PERCENTAGE -> formatPercent(
                (value as MonitoredValue.NumericalValue).value.toFloat()
            )

            Monitor.Type.DISK_SMART_HEALTH -> {
                (value as MonitoredValue.EnumValue<HealthStatus>).value.name.lowercase()
            }

            Monitor.Type.UPS_LOAD_WATT -> "${(value as MonitoredValue.NumericalValue).value}W"
            Monitor.Type.GPU_VRAM_USAGE -> byteFormatter.format((value as MonitoredValue.NumericalValue).value)
            Monitor.Type.GPU_TEMPERATURE -> temperatureFormatter.format((value as MonitoredValue.NumericalValue).value.toInt())
            Monitor.Type.GPU_UTILIZATION -> formatPercent((value as MonitoredValue.FractionalValue).value)
            Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> if ((value as MonitoredValue.ConditionalValue).value) "up to date" else "outdated"
            Monitor.Type.PACKAGE_UPDATES -> "${(value as MonitoredValue.NumericalValue).value}"
            Monitor.Type.PACKAGE_SECURITY_UPDATES -> "${(value as MonitoredValue.NumericalValue).value}"
        }
    }

    private fun formatPercent(percent: Float): String {
        return String.format("%.0f%%", percent)
    }
}
