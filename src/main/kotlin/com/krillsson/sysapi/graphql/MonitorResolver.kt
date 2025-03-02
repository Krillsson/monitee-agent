package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.domain.monitor.toConditionalValue
import com.krillsson.sysapi.core.domain.monitor.toFractionalValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.history.ContainersHistoryRepository
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.db.BasicHistorySystemLoadEntity
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.core.monitoring.monitors.*
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckService
import com.krillsson.sysapi.docker.ContainerManager
import com.krillsson.sysapi.graphql.domain.*
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

@Controller
@SchemaMapping(typeName = "Monitor")
class MonitorResolver(
    val historyRepository: HistoryRepository,
    val containersHistoryRepository: ContainersHistoryRepository,
    val eventManager: EventManager,
    val monitorManager: MonitorManager,
    val containerManager: ContainerManager,
    val webServerCheckService: WebServerCheckService,
    val metrics: Metrics
) {

    @SchemaMapping
    fun history(monitor: Monitor): List<MonitoredValueHistoryEntry> {
        val longTimeAgo = OffsetDateTime.now().minusYears(3).toInstant()
        return when (monitor.type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.WEBSERVER_UP -> {
                webServerCheckService.getHistoryForWebServerBetweenTimestamps(UUID.fromString(monitor.monitoredItemId), longTimeAgo, Instant.now()).map {
                    MonitoredValueHistoryEntry(it.timeStamp, it.isSuccessful.toConditionalValue().asMonitoredValue())
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_RUNNING -> {
                containersHistoryRepository.getHistoryLimitedToDates(requireNotNull(monitor.monitoredItemId), longTimeAgo, Instant.now()).map {
                    MonitoredValueHistoryEntry(it.timestamp, true.toConditionalValue().asMonitoredValue())
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_MEMORY_SPACE -> {
                containersHistoryRepository.getHistoryLimitedToDates(requireNotNull(monitor.monitoredItemId), longTimeAgo, Instant.now()).map {
                    MonitoredValueHistoryEntry(it.timestamp, it.metrics.memoryUsage.usageBytes.toNumericalValue().asMonitoredValue())
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_CPU_LOAD -> {
                containersHistoryRepository.getHistoryLimitedToDates(requireNotNull(monitor.monitoredItemId), longTimeAgo, Instant.now()).map {
                    MonitoredValueHistoryEntry(it.timestamp, it.metrics.cpuUsage.usagePercentTotal.toFractionalValue().asMonitoredValue())
                }
            }

            else -> historyRepository.getBasic().mapNotNull { it.asMonitoredValueHistoryEntry(monitor) }
        }
    }

    @SchemaMapping
    fun historyBetweenTimestamps(
        monitor: Monitor,
        @Argument from: Instant,
        @Argument to: Instant
    ): List<MonitoredValueHistoryEntry> {
        return historyRepository.getHistoryLimitedToDates(from, to).mapNotNull { it.asMonitoredValueHistoryEntry(monitor) }
    }

    @SchemaMapping
    fun events(monitor: Monitor): List<Event> {
        return eventManager.eventsForMonitorId(monitor.id)
    }

    @SchemaMapping
    fun pastEvents(monitor: Monitor) =
        eventManager.eventsForMonitorId(monitor.id).filterIsInstance(PastEvent::class.java)

    @SchemaMapping
    fun ongoingEvents(monitor: Monitor) =
        eventManager.eventsForMonitorId(monitor.id).filterIsInstance(OngoingEvent::class.java)

    @SchemaMapping
    fun maxValue(monitor: Monitor): MonitoredValue? {
        return monitorManager.getById(monitor.id)?.maxValue(metrics.systemInfo())
            ?.asMonitoredValue()
    }

    @SchemaMapping
    fun currentValue(monitor: Monitor): MonitoredValue? {
        return when (monitor.type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_RUNNING -> Selectors.forContainerConditionalMonitor(
                monitor.type
            )(
                containerManager.containersWithIds(listOf(monitor.monitoredItemId.orEmpty())),
                emptyList(),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_MEMORY_SPACE -> Selectors.forContainerNumericalMonitor(
                monitor.type
            )(
                emptyList(),
                listOfNotNull(containerManager.statsForContainer(monitor.monitoredItemId.orEmpty())),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_CPU_LOAD -> Selectors.forContainerFractionalMonitor(
                monitor.type
            )(
                emptyList(),
                listOfNotNull(containerManager.statsForContainer(monitor.monitoredItemId.orEmpty())),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.WEBSERVER_UP -> {
                (webServerCheckService.getStatusForWebServer(UUID.fromString(monitor.monitoredItemId))?.responseCode == 200).toConditionalValue().asMonitoredValue()
            }

            else -> when (monitor.type.valueType) {
                com.krillsson.sysapi.core.monitoring.Monitor.ValueType.Numerical -> Selectors.forNumericalMonitorType(
                    monitor.type
                )(
                    metrics.systemLoad(),
                    monitor.monitoredItemId
                )

                com.krillsson.sysapi.core.monitoring.Monitor.ValueType.Fractional -> Selectors.forFractionalMonitorType(
                    monitor.type
                )(
                    metrics.systemLoad(),
                    monitor.monitoredItemId
                )

                com.krillsson.sysapi.core.monitoring.Monitor.ValueType.Conditional -> Selectors.forConditionalMonitorType(
                    monitor.type
                )(
                    metrics.systemLoad(),
                    monitor.monitoredItemId
                )
            }?.asMonitoredValue()
        }
    }

    private fun BasicHistorySystemLoadEntity.asMonitoredValueHistoryEntry(monitor: Monitor): MonitoredValueHistoryEntry? {
        val value: MonitoredValue? = when (monitor.type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CPU_LOAD -> CpuMonitor.value(historyRepository.getCpuLoadById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> LoadAverageMonitorOneMinute.value(historyRepository.getCpuLoadById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> LoadAverageMonitorFiveMinutes.value(historyRepository.getCpuLoadById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> LoadAverageMonitorFifteenMinutes.value(historyRepository.getCpuLoadById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CPU_TEMP -> CpuTemperatureMonitor.value(historyRepository.getCpuLoadById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.FILE_SYSTEM_SPACE -> FileSystemSpaceMonitor.value(historyRepository.getFileSystemLoadsById(id), monitor.monitoredItemId)?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_READ_RATE -> DiskReadRateMonitor.value(historyRepository.getDiskLoadsById(id), monitor.monitoredItemId)?.asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_WRITE_RATE -> DiskWriteRateMonitor.value(historyRepository.getDiskLoadsById(id), monitor.monitoredItemId)?.asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_TEMPERATURE -> DiskTemperatureMonitor.value(historyRepository.getDiskLoadsById(id), monitor.monitoredItemId)?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.MEMORY_SPACE -> MemorySpaceMonitor.value(historyRepository.getMemoryLoadById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.MEMORY_USED -> MemoryUsedMonitor.value(historyRepository.getMemoryLoadById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_UP -> NetworkUpMonitor.value(historyRepository.getNetworkInterfaceLoadsById(id), monitor.monitoredItemId)?.asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_UPLOAD_RATE -> NetworkUploadRateMonitor.value(historyRepository.getNetworkInterfaceLoadsById(id), monitor.monitoredItemId)?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_DOWNLOAD_RATE -> NetworkDownloadRateMonitor.value(historyRepository.getNetworkInterfaceLoadsById(id), monitor.monitoredItemId)?.asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_MEMORY_SPACE -> NumericalValue(0L)
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_CPU_LOAD -> FractionalValue(0f)
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_EXISTS -> false.toConditionalValue().asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONNECTIVITY -> ConnectivityMonitor.value(historyRepository.getConnectivityById(id)).asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.EXTERNAL_IP_CHANGED -> ExternalIpChangedMonitor.value(historyRepository.getConnectivityById(id))?.asMonitoredValue()

            else -> throw IllegalStateException("Illegal type ${monitor.type.name}")
        }

        return value?.let { MonitoredValueHistoryEntry(date, it) }
    }
}
