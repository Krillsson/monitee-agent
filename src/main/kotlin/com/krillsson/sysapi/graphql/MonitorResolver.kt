package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.db.BasicHistorySystemLoadEntity
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.MonitorableItem
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.core.monitoring.monitors.*
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckService
import com.krillsson.sysapi.docker.ContainersHistoryRepository
import com.krillsson.sysapi.graphql.domain.*
import com.krillsson.sysapi.ups.UpsMetricsHistoryRepository
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
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
    val upsMetricsHistoryRepository: UpsMetricsHistoryRepository,
    val webServerCheckService: WebServerCheckService,
    val metrics: Metrics
) {

    @SchemaMapping
    fun history(monitor: Monitor): List<MonitoredValueHistoryEntry> {
        val longTimeAgo = OffsetDateTime.now().minusYears(3).toInstant()
        return when (monitor.type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.WEBSERVER_UP -> {
                webServerCheckService.getHistoryForWebServerBetweenTimestamps(
                    UUID.fromString(monitor.monitoredItemId),
                    longTimeAgo,
                    Instant.now()
                ).map {
                    MonitoredValueHistoryEntry(it.timeStamp, it.isSuccessful.toConditionalValue().asMonitoredValue())
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_RUNNING -> {
                containersHistoryRepository.getHistoryLimitedToDates(
                    requireNotNull(monitor.monitoredItemId),
                    longTimeAgo,
                    Instant.now()
                ).map {
                    MonitoredValueHistoryEntry(it.timestamp, true.toConditionalValue().asMonitoredValue())
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_MEMORY_SPACE -> {
                containersHistoryRepository.getHistoryLimitedToDates(
                    requireNotNull(monitor.monitoredItemId),
                    longTimeAgo,
                    Instant.now()
                ).map {
                    MonitoredValueHistoryEntry(
                        it.timestamp,
                        it.metrics.memoryUsage.usageBytes.toNumericalValue().asMonitoredValue()
                    )
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_CPU_LOAD -> {
                containersHistoryRepository.getHistoryLimitedToDates(
                    requireNotNull(monitor.monitoredItemId),
                    longTimeAgo,
                    Instant.now()
                ).map {
                    MonitoredValueHistoryEntry(
                        it.timestamp,
                        it.metrics.cpuUsage.usagePercentTotal.toFractionalValue().asMonitoredValue()
                    )
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.UPS_LOAD_PERCENTAGE -> {
                upsMetricsHistoryRepository.getHistoryLimitedToDates(
                    requireNotNull(monitor.monitoredItemId),
                    longTimeAgo,
                    Instant.now()
                ).map {
                    MonitoredValueHistoryEntry(
                        it.timestamp,
                        it.metrics.loadPercent?.toNumericalValue()?.asMonitoredValue()
                            ?: com.krillsson.sysapi.core.monitoring.MonitoredValue.NumericalValue(-1)
                                .asMonitoredValue()
                    )
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.UPS_OPERATING_NORMALLY -> {
                upsMetricsHistoryRepository.getHistoryLimitedToDates(
                    requireNotNull(monitor.monitoredItemId),
                    longTimeAgo,
                    Instant.now()
                ).map {
                    MonitoredValueHistoryEntry(
                        it.timestamp,
                        it.metrics.isOperatingNormally().toConditionalValue().asMonitoredValue()
                    )
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
        return when (monitor.type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.WEBSERVER_UP -> {
                webServerCheckService.getHistoryForWebServerBetweenTimestamps(
                    UUID.fromString(monitor.monitoredItemId),
                    from,
                    to
                ).map {
                    MonitoredValueHistoryEntry(it.timeStamp, it.isSuccessful.toConditionalValue().asMonitoredValue())
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_RUNNING -> {
                containersHistoryRepository.getHistoryLimitedToDates(requireNotNull(monitor.monitoredItemId), from, to)
                    .map {
                        MonitoredValueHistoryEntry(it.timestamp, true.toConditionalValue().asMonitoredValue())
                    }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_MEMORY_SPACE -> {
                containersHistoryRepository.getHistoryLimitedToDates(requireNotNull(monitor.monitoredItemId), from, to)
                    .map {
                        MonitoredValueHistoryEntry(
                            it.timestamp,
                            it.metrics.memoryUsage.usageBytes.toNumericalValue().asMonitoredValue()
                        )
                    }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONTAINER_CPU_LOAD -> {
                containersHistoryRepository.getHistoryLimitedToDates(requireNotNull(monitor.monitoredItemId), from, to)
                    .map {
                        MonitoredValueHistoryEntry(
                            it.timestamp,
                            it.metrics.cpuUsage.usagePercentTotal.toFractionalValue().asMonitoredValue()
                        )
                    }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.UPS_LOAD_PERCENTAGE -> {
                upsMetricsHistoryRepository.getHistoryLimitedToDates(
                    requireNotNull(monitor.monitoredItemId),
                    from,
                    to
                ).map {
                    MonitoredValueHistoryEntry(
                        it.timestamp,
                        it.metrics.loadPercent?.toNumericalValue()?.asMonitoredValue()
                            ?: com.krillsson.sysapi.core.monitoring.MonitoredValue.NumericalValue(-1)
                                .asMonitoredValue()
                    )
                }
            }

            com.krillsson.sysapi.core.monitoring.Monitor.Type.UPS_OPERATING_NORMALLY -> {
                upsMetricsHistoryRepository.getHistoryLimitedToDates(
                    requireNotNull(monitor.monitoredItemId),
                    from,
                    to
                ).map {
                    MonitoredValueHistoryEntry(
                        it.timestamp,
                        it.metrics.isOperatingNormally().toConditionalValue().asMonitoredValue()
                    )
                }
            }

            else -> historyRepository.getHistoryLimitedToDates(from, to)
                .mapNotNull { it.asMonitoredValueHistoryEntry(monitor) }
        }
    }

    @SchemaMapping
    fun events(monitor: Monitor): List<Event> {
        return eventManager.eventsForMonitorId(monitor.id)
    }

    @BatchMapping(field = "monitoredItem", typeName = "Monitor")
    fun monitoredItem(monitors: Collection<Monitor>): Map<Monitor, MonitorableItem> {
        val itemIds = monitors.map { it.id }
        val items = monitorManager.getMonitorableItemsForMonitors(itemIds).associateBy { it.first }
        return monitors.associateWith { requireNotNull(items[it.id]?.second) }
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

    @BatchMapping(field = "currentValue", typeName = "Monitor")
    fun currentValue(monitors: Collection<Monitor>): Map<Monitor, MonitoredValue?> {
        val itemIds = monitors.map { it.id }
        val items = monitorManager.getMonitorableItemsForMonitors(itemIds).associateBy { it.first }
        return monitors.associateWith { requireNotNull(items[it.id]?.second?.currentValue?.asMonitoredValue()) }
    }

    private fun BasicHistorySystemLoadEntity.asMonitoredValueHistoryEntry(monitor: Monitor): MonitoredValueHistoryEntry? {
        val value: MonitoredValue? = when (monitor.type) {
            com.krillsson.sysapi.core.monitoring.Monitor.Type.CPU_LOAD -> CpuMonitor.value(
                historyRepository.getCpuLoadById(
                    id
                )
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> LoadAverageMonitorOneMinute.value(
                historyRepository.getCpuLoadById(id)
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> LoadAverageMonitorFiveMinutes.value(
                historyRepository.getCpuLoadById(id)
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> LoadAverageMonitorFifteenMinutes.value(
                historyRepository.getCpuLoadById(id)
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CPU_TEMP -> CpuTemperatureMonitor.value(
                historyRepository.getCpuLoadById(
                    id
                )
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.FILE_SYSTEM_SPACE -> FileSystemSpaceMonitor.value(
                historyRepository.getFileSystemLoadsById(id),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_READ_RATE -> DiskReadRateMonitor.value(
                historyRepository.getDiskLoadsById(id),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_WRITE_RATE -> DiskWriteRateMonitor.value(
                historyRepository.getDiskLoadsById(id),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_TEMPERATURE -> DiskTemperatureMonitor.value(
                historyRepository.getDiskLoadsById(id),
                monitor.monitoredItemId
            )?.asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.DISK_SMART_HEALTH -> DiskSmartHealthMonitor.value(
                historyRepository.getDiskLoadsById(id),
                monitor.monitoredItemId
            )?.asMonitoredValue()
            com.krillsson.sysapi.core.monitoring.Monitor.Type.MEMORY_SPACE -> MemorySpaceMonitor.value(
                historyRepository.getMemoryLoadById(
                    id
                )
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.MEMORY_USED -> MemoryUsedMonitor.value(
                historyRepository.getMemoryLoadById(
                    id
                )
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_UP -> NetworkUpMonitor.value(
                historyRepository.getNetworkInterfaceLoadsById(
                    id
                ), monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_UPLOAD_RATE -> NetworkUploadRateMonitor.value(
                historyRepository.getNetworkInterfaceLoadsById(id),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.NETWORK_DOWNLOAD_RATE -> NetworkDownloadRateMonitor.value(
                historyRepository.getNetworkInterfaceLoadsById(id),
                monitor.monitoredItemId
            )?.asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_MEMORY_SPACE -> NumericalValue(0L)
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_CPU_LOAD -> FractionalValue(0f)
            com.krillsson.sysapi.core.monitoring.Monitor.Type.PROCESS_EXISTS -> false.toConditionalValue()
                .asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.CONNECTIVITY -> ConnectivityMonitor.value(
                historyRepository.getConnectivityById(id)
            ).asMonitoredValue()

            com.krillsson.sysapi.core.monitoring.Monitor.Type.EXTERNAL_IP_CHANGED -> ExternalIpChangedMonitor.value(
                historyRepository.getConnectivityById(id)
            )?.asMonitoredValue()

            else -> throw IllegalStateException("Illegal type ${monitor.type.name}")
        }

        return value?.let { MonitoredValueHistoryEntry(date, it) }
    }
}
