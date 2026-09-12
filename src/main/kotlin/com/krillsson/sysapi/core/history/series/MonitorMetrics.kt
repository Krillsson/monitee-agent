package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.history.series.MetricId.Companion.HOST_WIDE

data class MonitorSeries(
    val metric: MetricId,
    val itemId: String
)

object MonitorMetrics {

    fun seriesFor(type: Monitor.Type, monitoredItemId: String?): MonitorSeries? {
        val metric = metricFor(type) ?: return null
        val itemId = if (itemIsHostWide(type)) HOST_WIDE else monitoredItemId ?: return null
        return MonitorSeries(metric, itemId)
    }

    fun collapse(point: MetricHistoryPoint, metric: MetricId): MonitoredValue = when (metric.kind) {
        MetricKind.GAUGE -> point.avg.asValue(metric)
        MetricKind.COUNTER -> point.last.asValue(metric)
        MetricKind.FLAG -> MonitoredValue.ConditionalValue(point.min == 1.0)
    }

    private fun Double.asValue(metric: MetricId): MonitoredValue = when (metric.valueType) {
        MetricValueType.NUMERICAL -> MonitoredValue.NumericalValue(toLong())
        MetricValueType.FRACTIONAL -> MonitoredValue.FractionalValue(toFloat())
        MetricValueType.CONDITIONAL -> MonitoredValue.ConditionalValue(this == 1.0)
    }

    private fun itemIsHostWide(type: Monitor.Type) = when (type) {
        Monitor.Type.CPU_LOAD,
        Monitor.Type.CPU_TEMP,
        Monitor.Type.LOAD_AVERAGE_ONE_MINUTE,
        Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES,
        Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES,
        Monitor.Type.MEMORY_SPACE,
        Monitor.Type.MEMORY_USED,
        Monitor.Type.CONNECTIVITY,
        Monitor.Type.EXTERNAL_IP_CHANGED -> true

        else -> false
    }

    private fun metricFor(type: Monitor.Type): MetricId? = when (type) {
        Monitor.Type.CPU_LOAD -> MetricId.CPU_USAGE_PERCENT
        Monitor.Type.CPU_TEMP -> MetricId.CPU_TEMPERATURE
        Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> MetricId.LOAD_AVERAGE_1M
        Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> MetricId.LOAD_AVERAGE_5M
        Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> MetricId.LOAD_AVERAGE_15M
        Monitor.Type.MEMORY_SPACE -> MetricId.MEMORY_AVAILABLE_BYTES
        Monitor.Type.MEMORY_USED -> MetricId.MEMORY_USED_BYTES
        Monitor.Type.FILE_SYSTEM_SPACE -> MetricId.FILESYSTEM_FREE_BYTES
        Monitor.Type.DISK_READ_RATE -> MetricId.DISK_READ_BYTES_PER_SECOND
        Monitor.Type.DISK_WRITE_RATE -> MetricId.DISK_WRITE_BYTES_PER_SECOND
        Monitor.Type.DISK_TEMPERATURE -> MetricId.DISK_TEMPERATURE
        Monitor.Type.NETWORK_UP -> MetricId.NETWORK_UP
        Monitor.Type.NETWORK_UPLOAD_RATE -> MetricId.NETWORK_TX_BYTES_PER_SECOND
        Monitor.Type.NETWORK_DOWNLOAD_RATE -> MetricId.NETWORK_RX_BYTES_PER_SECOND
        Monitor.Type.CONNECTIVITY -> MetricId.CONNECTIVITY_UP
        Monitor.Type.EXTERNAL_IP_CHANGED -> MetricId.CONNECTIVITY_EXTERNAL_IP_STABLE
        Monitor.Type.GPU_UTILIZATION -> MetricId.GPU_CORE_PERCENT
        Monitor.Type.GPU_VRAM_USAGE -> MetricId.GPU_VRAM_USED_BYTES
        Monitor.Type.GPU_TEMPERATURE -> MetricId.GPU_TEMPERATURE
        Monitor.Type.CONTAINER_CPU_LOAD -> MetricId.CONTAINER_CPU_PERCENT
        Monitor.Type.CONTAINER_MEMORY_SPACE -> MetricId.CONTAINER_MEMORY_USED_BYTES
        Monitor.Type.CONTAINER_RUNNING -> MetricId.CONTAINER_RUNNING
        Monitor.Type.UPS_LOAD_PERCENTAGE -> MetricId.UPS_LOAD_PERCENT
        Monitor.Type.UPS_LOAD_WATT -> MetricId.UPS_LOAD_WATTS
        Monitor.Type.UPS_OPERATING_NORMALLY -> MetricId.UPS_OPERATING_NORMALLY

        Monitor.Type.DISK_SMART_HEALTH,
        Monitor.Type.WEBSERVER_UP,
        Monitor.Type.CHECK_LATENCY,
        Monitor.Type.CONTAINER_UPDATE_AVAILABLE,
        Monitor.Type.PROCESS_MEMORY_SPACE,
        Monitor.Type.PROCESS_CPU_LOAD,
        Monitor.Type.PROCESS_EXISTS -> null
    }
}
