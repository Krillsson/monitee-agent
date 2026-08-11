package com.krillsson.sysapi.mqtt

import com.krillsson.sysapi.core.monitoring.Monitor

object MqttMonitorLabels {

    private val identifier = Regex("[0-9a-f-]{16,}")

    fun name(type: Monitor.Type, monitoredItemId: String?): String {
        val item = monitoredItemId?.trim()?.trimStart('/')?.takeIf { it.isNotBlank() }?.abbreviateIdentifier()
        return listOfNotNull(label(type), item, "alert").joinToString(" ")
    }

    private fun String.abbreviateIdentifier() = if (identifier.matches(this)) take(12) else this

    private fun label(type: Monitor.Type) = when (type) {
        Monitor.Type.CPU_LOAD -> "CPU load"
        Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> "Load average 1m"
        Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> "Load average 5m"
        Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> "Load average 15m"
        Monitor.Type.CPU_TEMP -> "CPU temperature"
        Monitor.Type.FILE_SYSTEM_SPACE -> "Space on"
        Monitor.Type.DISK_READ_RATE -> "Read rate on"
        Monitor.Type.DISK_TEMPERATURE -> "Disk temperature"
        Monitor.Type.DISK_WRITE_RATE -> "Write rate on"
        Monitor.Type.DISK_SMART_HEALTH -> "SMART health"
        Monitor.Type.MEMORY_SPACE -> "Memory space"
        Monitor.Type.MEMORY_USED -> "Memory used"
        Monitor.Type.NETWORK_UP -> "Network"
        Monitor.Type.NETWORK_UPLOAD_RATE -> "Upload rate on"
        Monitor.Type.NETWORK_DOWNLOAD_RATE -> "Download rate on"
        Monitor.Type.CONTAINER_RUNNING -> "Container"
        Monitor.Type.CONTAINER_MEMORY_SPACE -> "Container memory"
        Monitor.Type.CONTAINER_CPU_LOAD -> "Container CPU load"
        Monitor.Type.CONTAINER_UPDATE_AVAILABLE -> "Container update"
        Monitor.Type.PROCESS_MEMORY_SPACE -> "Process memory"
        Monitor.Type.PROCESS_CPU_LOAD -> "Process CPU load"
        Monitor.Type.PROCESS_EXISTS -> "Process"
        Monitor.Type.CONNECTIVITY -> "Connectivity"
        Monitor.Type.WEBSERVER_UP -> "Check"
        Monitor.Type.CHECK_LATENCY -> "Check latency"
        Monitor.Type.EXTERNAL_IP_CHANGED -> "External IP"
        Monitor.Type.UPS_OPERATING_NORMALLY -> "UPS"
        Monitor.Type.UPS_LOAD_PERCENTAGE -> "UPS load"
        Monitor.Type.UPS_LOAD_WATT -> "UPS power"
        Monitor.Type.GPU_VRAM_USAGE -> "GPU VRAM"
        Monitor.Type.GPU_TEMPERATURE -> "GPU temperature"
        Monitor.Type.GPU_UTILIZATION -> "GPU load"
    }
}
