package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.asMonitorType
import com.krillsson.sysapi.core.monitoring.monitors.*
import com.krillsson.sysapi.util.toOffsetDateTime
import java.time.Instant
import java.util.*

interface MonitoredValue

data class NumericalValue(val number: Long) : com.krillsson.sysapi.graphql.domain.MonitoredValue
data class FractionalValue(val fraction: Float) : com.krillsson.sysapi.graphql.domain.MonitoredValue
data class ConditionalValue(val condition: Boolean) : com.krillsson.sysapi.graphql.domain.MonitoredValue
data class EnumValue(
    val name: String,
    val ordinal: Int,
    val type: com.krillsson.sysapi.core.monitoring.Monitor.Type
) : com.krillsson.sysapi.graphql.domain.MonitoredValue

data class MonitorableEnum(
    val type: com.krillsson.sysapi.core.monitoring.Monitor.Type,
    val entries: List<EnumValue>
)

data class MonitoredValueHistoryEntry(
    val timestamp: Instant,
    val value: com.krillsson.sysapi.graphql.domain.MonitoredValue
) {
    fun dateTime() = timestamp.toOffsetDateTime()
    fun date() = timestamp.toOffsetDateTime()

}

data class Monitor(
    val id: UUID,
    val inertiaInSeconds: Int,
    val monitoredItemId: String?,
    val threshold: com.krillsson.sysapi.graphql.domain.MonitoredValue,
    val type: com.krillsson.sysapi.core.monitoring.Monitor.Type
)

fun com.krillsson.sysapi.core.monitoring.Monitor<MonitoredValue>.asMonitor(): Monitor {
    return Monitor(
        id,
        config.inertia.seconds.toInt(),
        config.monitoredItemId,
        config.threshold.asMonitoredValue(),
        type
    )
}

fun MonitoredValue.asMonitoredValue(): com.krillsson.sysapi.graphql.domain.MonitoredValue {
    return when (this) {
        is MonitoredValue.ConditionalValue -> ConditionalValue(value)
        is MonitoredValue.FractionalValue -> FractionalValue(value)
        is MonitoredValue.NumericalValue -> NumericalValue(value)
        is MonitoredValue.EnumValue<*> -> EnumValue(value.name, value.ordinal, this.asMonitorType())
    }
}

data class MonitorableItemsInput(
    val type: com.krillsson.sysapi.core.monitoring.Monitor.Type
)

data class MonitorableItemsOutput(
    val items: List<MonitorableItem>
)

data class MonitorableItem(
    val id: String?,
    val name: String,
    val description: String?,
    val maxValue: com.krillsson.sysapi.graphql.domain.MonitoredValue,
    val currentValue: com.krillsson.sysapi.graphql.domain.MonitoredValue,
    val type: com.krillsson.sysapi.core.monitoring.Monitor.Type
)

object Selectors {

    fun forConditionalMonitorType(type: com.krillsson.sysapi.core.monitoring.Monitor.Type): ConditionalValueSelector =
        when (type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_UP -> NetworkUpMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_EXISTS -> ProcessExistsMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONNECTIVITY -> ConnectivityMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.EXTERNAL_IP_CHANGED -> ExternalIpChangedMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_RUNNING -> throw IllegalArgumentException("Use forContainerConditionalMonitor() method")
            else -> throw IllegalArgumentException("$type is a ${type.valueType} type")
        }

    fun forNumericalMonitorType(type: com.krillsson.sysapi.core.monitoring.Monitor.Type): NumericalValueSelector =
        when (type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CPU_TEMP -> CpuTemperatureMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_READ_RATE -> DiskReadRateMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_WRITE_RATE -> DiskWriteRateMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_TEMPERATURE -> DiskTemperatureMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.FILE_SYSTEM_SPACE -> FileSystemSpaceMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.MEMORY_SPACE -> MemorySpaceMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.MEMORY_USED -> MemoryUsedMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_UPLOAD_RATE -> NetworkUploadRateMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_DOWNLOAD_RATE -> NetworkDownloadRateMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_MEMORY_SPACE -> ProcessMemoryMonitor.selector
            else -> throw IllegalArgumentException("$type is a ${type.valueType} type")
        }

    fun forFractionalMonitorType(type: com.krillsson.sysapi.core.monitoring.Monitor.Type): FractionalValueSelector =
        when (type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CPU_LOAD -> CpuMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_CPU_LOAD -> ProcessCpuMonitor.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> LoadAverageMonitorOneMinute.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> LoadAverageMonitorFiveMinutes.selector
            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> LoadAverageMonitorFifteenMinutes.selector
            else -> throw IllegalArgumentException("$type is a ${type.valueType} type")
        }

    fun forContainerConditionalMonitor(type: com.krillsson.sysapi.core.monitoring.Monitor.Type): ContainerConditionalValueSelector =
        when (type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_RUNNING -> ContainerRunningMonitor.selector
            else -> throw IllegalArgumentException("Use forConditionalMonitorType() method")
        }

    fun forContainerNumericalMonitor(type: com.krillsson.sysapi.core.monitoring.Monitor.Type): ContainerNumericalValueSelector =
        when (type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_MEMORY_SPACE -> ContainerMemoryMonitor.selector
            else -> throw IllegalArgumentException("Use forConditionalMonitorType() method")
        }

    fun forContainerFractionalMonitor(type: com.krillsson.sysapi.core.monitoring.Monitor.Type): ContainerFractionalValueSelector =
        when (type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_CPU_LOAD -> ContainerCpuMonitor.selector
            else -> throw IllegalArgumentException("Use forConditionalMonitorType() method")
        }
}

