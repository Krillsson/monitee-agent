package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.monitoring.monitors.*
import com.krillsson.sysapi.smart.HealthStatus
import java.util.*

object MonitorFactory {
    @Suppress("UNCHECKED_CAST")
    fun createMonitor(type: Monitor.Type, id: UUID, config: MonitorConfig<MonitoredValue>): Monitor<MonitoredValue> {
        return when (type) {
            Monitor.Type.CPU_LOAD -> CpuMonitor(id, config as MonitorConfig<MonitoredValue.FractionalValue>)
            Monitor.Type.CPU_TEMP -> CpuTemperatureMonitor(id, config as MonitorConfig<MonitoredValue.NumericalValue>)
            Monitor.Type.MEMORY_SPACE -> MemorySpaceMonitor(id, config as MonitorConfig<MonitoredValue.NumericalValue>)
            Monitor.Type.MEMORY_USED -> MemoryUsedMonitor(id, config as MonitorConfig<MonitoredValue.NumericalValue>)
            Monitor.Type.NETWORK_UP -> NetworkUpMonitor(id, config as MonitorConfig<MonitoredValue.ConditionalValue>)
            Monitor.Type.CONTAINER_RUNNING -> ContainerRunningMonitor(
                id,
                config as MonitorConfig<MonitoredValue.ConditionalValue>
            )

            Monitor.Type.PROCESS_MEMORY_SPACE -> ProcessMemoryMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.PROCESS_CPU_LOAD -> ProcessCpuMonitor(
                id,
                config as MonitorConfig<MonitoredValue.FractionalValue>
            )

            Monitor.Type.PROCESS_EXISTS -> ProcessExistsMonitor(
                id,
                config as MonitorConfig<MonitoredValue.ConditionalValue>
            )

            Monitor.Type.CONNECTIVITY -> ConnectivityMonitor(
                id,
                config as MonitorConfig<MonitoredValue.ConditionalValue>
            )

            Monitor.Type.EXTERNAL_IP_CHANGED -> ExternalIpChangedMonitor(
                id,
                config as MonitorConfig<MonitoredValue.ConditionalValue>
            )

            Monitor.Type.NETWORK_UPLOAD_RATE -> NetworkUploadRateMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.NETWORK_DOWNLOAD_RATE -> NetworkDownloadRateMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.FILE_SYSTEM_SPACE -> FileSystemSpaceMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.DISK_READ_RATE -> DiskReadRateMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.DISK_WRITE_RATE -> DiskWriteRateMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> LoadAverageMonitorOneMinute(
                id,
                config as MonitorConfig<MonitoredValue.FractionalValue>
            )

            Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> LoadAverageMonitorFiveMinutes(
                id,
                config as MonitorConfig<MonitoredValue.FractionalValue>
            )

            Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> LoadAverageMonitorFifteenMinutes(
                id,
                config as MonitorConfig<MonitoredValue.FractionalValue>
            )

            Monitor.Type.CONTAINER_MEMORY_SPACE -> ContainerMemoryMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.CONTAINER_CPU_LOAD -> ContainerCpuMonitor(
                id,
                config as MonitorConfig<MonitoredValue.FractionalValue>
            )

            Monitor.Type.WEBSERVER_UP -> WebServerUpMonitor(
                id,
                config as MonitorConfig<MonitoredValue.ConditionalValue>
            )

            Monitor.Type.DISK_TEMPERATURE -> DiskTemperatureMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.UPS_OPERATING_NORMALLY -> UpsOperatingNormallyMonitor(
                id,
                config as MonitorConfig<MonitoredValue.ConditionalValue>
            )
            Monitor.Type.UPS_LOAD_PERCENTAGE -> UpsLoadPercentageMonitor(
                id,
                config as MonitorConfig<MonitoredValue.NumericalValue>
            )

            Monitor.Type.SMART_HEALTH -> SmartHealthStatusMonitor(
                id,
                config as MonitorConfig<MonitoredValue.EnumValue<HealthStatus>>
            )
        }
    }
}