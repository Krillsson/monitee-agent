package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.monitor.toConditionalValue
import com.krillsson.sysapi.core.domain.monitor.toFractionalValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.domain.system.SystemLoad
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckService
import com.krillsson.sysapi.docker.ContainerManager
import org.springframework.stereotype.Component
import java.util.*

@Component
class MonitorInputCreator(
    private val metrics: Metrics,
    private val containerManager: ContainerManager,
    private val webServerCheckService: WebServerCheckService
) {

    companion object {
        const val THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND = 14_000L * 1024 * 1024
    }

    private val networkTypes = listOf(
        Monitor.Type.NETWORK_DOWNLOAD_RATE,
        Monitor.Type.NETWORK_UPLOAD_RATE,
        Monitor.Type.NETWORK_UP
    )
    private val diskTypes = listOf(
        Monitor.Type.DISK_READ_RATE,
        Monitor.Type.DISK_WRITE_RATE,
        Monitor.Type.DISK_TEMPERATURE
    )
    private val fileSystemTypes = listOf(
        Monitor.Type.FILE_SYSTEM_SPACE
    )

    private val processesTypes = listOf(
        Monitor.Type.PROCESS_MEMORY_SPACE,
        Monitor.Type.PROCESS_CPU_LOAD,
        Monitor.Type.PROCESS_EXISTS
    )

    private val containerTypes = listOf<Monitor.Type>(
        Monitor.Type.CONTAINER_RUNNING
    )

    private val webserverCheckTypes = listOf<Monitor.Type>(
        Monitor.Type.WEBSERVER_UP
    )

    private val containerStatisticsTypes = listOf<Monitor.Type>(
        Monitor.Type.CONTAINER_MEMORY_SPACE, Monitor.Type.CONTAINER_CPU_LOAD
    )

    private fun List<Monitor<*>>.idsSubset(types: List<Monitor.Type>) =
        filter { types.contains(it.type) }
            .mapNotNull { it.config.monitoredItemId }

    fun createInput(
        activeTypes: List<Monitor<*>>,
    ): MonitorInput {
        val containerIds = activeTypes.idsSubset(containerTypes)
        val containerStatisticsIds = activeTypes.idsSubset(containerStatisticsTypes)
        val nicLoads = if (activeTypes.any { networkTypes.contains(it.type) }) metrics.networkMetrics()
            .networkInterfaceLoads() else emptyList()
        val diskLoads =
            if (activeTypes.any { diskTypes.contains(it.type) }) metrics.diskMetrics().diskLoads() else emptyList()
        val fileSystemLoads = if (activeTypes.any { fileSystemTypes.contains(it.type) }) metrics.fileSystemMetrics()
            .fileSystemLoads() else emptyList()
        val processes = activeTypes.filter { processesTypes.contains(it.type) }.mapNotNull {
            it.config.monitoredItemId?.toInt()?.let { pid ->
                metrics.processesMetrics().getProcessByPid(pid).orElse(null)
            }
        }
        val webserverChecks = activeTypes.filter { webserverCheckTypes.contains(it.type) }.mapNotNull {
            webServerCheckService.getStatusForWebServer(UUID.fromString(it.config.monitoredItemId))
        }

        val load = SystemLoad(
            uptime = metrics.cpuMetrics().uptime(),
            systemLoadAverage = metrics.cpuMetrics().cpuLoad().systemLoadAverage,
            cpuLoad = metrics.cpuMetrics().cpuLoad(),
            networkInterfaceLoads = nicLoads,
            connectivity = metrics.networkMetrics().connectivity(),
            diskLoads = diskLoads,
            fileSystemLoads = fileSystemLoads,
            memory = metrics.memoryMetrics().memoryLoad(),
            processes = processes,
            gpuLoads = emptyList(),
            motherboardHealth = metrics.motherboardMetrics().motherboardHealth()
        )
        val containers =
            if (containerIds.isNotEmpty()) containerManager.containersWithIds(containerIds) else emptyList()
        val containersStats = containerStatisticsIds.mapNotNull { id -> containerManager.statsForContainer(id) }

        return MonitorInput(
            load,
            containers,
            containersStats,
            webserverChecks
        )
    }

    fun getMonitorableItemForType(type: Monitor.Type): List<MonitorableItem> {
        return when (type) {
            Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    MonitorableItem(
                        null,
                        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
                        null,
                        cpuInfo.centralProcessor.logicalProcessorCount.toFloat().toFractionalValue(),
                        cpuMetrics.loadAverages.oneMinute.toFractionalValue(),
                        type
                    )
                )
            }

            Monitor.Type.CPU_LOAD -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    MonitorableItem(
                        null,
                        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
                        null,
                        (100f * cpuInfo.centralProcessor.logicalProcessorCount).toFractionalValue(),
                        cpuMetrics.usagePercentage.toFractionalValue(),
                        type
                    )
                )
            }

            Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    MonitorableItem(
                        null,
                        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
                        null,
                        cpuInfo.centralProcessor.logicalProcessorCount.toFloat().toFractionalValue(),
                        cpuMetrics.loadAverages.fiveMinutes.toFractionalValue(),
                        type
                    )
                )
            }

            Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    MonitorableItem(
                        null,
                        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
                        null,
                        cpuInfo.centralProcessor.logicalProcessorCount.toFloat().toFractionalValue(),
                        cpuMetrics.loadAverages.fifteenMinutes.toFractionalValue(),
                        type
                    )
                )
            }

            Monitor.Type.CPU_TEMP -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    MonitorableItem(
                        null,
                        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
                        null,
                        MonitoredValue.NumericalValue(120),
                        cpuMetrics.cpuHealth.temperatures.average().toNumericalValue(),
                        type
                    )
                )
            }

            Monitor.Type.FILE_SYSTEM_SPACE -> {
                val fileSystemLoads = metrics.fileSystemMetrics().fileSystemLoads().associateBy { it.id }
                metrics.fileSystemMetrics().fileSystems().mapNotNull {
                    fileSystemLoads[it.id]?.let { load ->
                        MonitorableItem(
                            id = it.id,
                            name = it.label,
                            description = it.description,
                            maxValue = load.totalSpaceBytes.toNumericalValue(),
                            currentValue = load.usableSpaceBytes.toNumericalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.DISK_READ_RATE -> {
                val diskLoads = metrics.diskMetrics().diskLoads().associateBy { it.name }
                metrics.diskMetrics().disks().mapNotNull {
                    diskLoads[it.name]?.let { load ->
                        MonitorableItem(
                            id = it.name,
                            name = it.name,
                            description = it.serial,
                            maxValue = THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND.toNumericalValue(),
                            currentValue = load.speed.readBytesPerSecond.toNumericalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.DISK_WRITE_RATE -> {
                val diskLoads = metrics.diskMetrics().diskLoads().associateBy { it.name }
                metrics.diskMetrics().disks().mapNotNull {
                    diskLoads[it.name]?.let { load ->
                        MonitorableItem(
                            id = it.name,
                            name = it.name,
                            description = it.serial,
                            maxValue = THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND.toNumericalValue(),
                            currentValue = load.speed.writeBytesPerSecond.toNumericalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.MEMORY_SPACE -> {
                val load = metrics.memoryMetrics().memoryLoad()
                listOf(
                    MonitorableItem(
                        id = null,
                        name = "Memory space available",
                        description = null,
                        maxValue = load.totalBytes.toNumericalValue(),
                        currentValue = load.availableBytes.toNumericalValue(),
                        type = type
                    )
                )
            }

            Monitor.Type.MEMORY_USED -> {
                val load = metrics.memoryMetrics().memoryLoad()
                listOf(
                    MonitorableItem(
                        id = null,
                        name = "Memory space used",
                        description = null,
                        maxValue = load.totalBytes.toNumericalValue(),
                        currentValue = load.usedBytes.toNumericalValue(),
                        type = type
                    )
                )
            }

            Monitor.Type.NETWORK_UP -> {
                val nicLoads = metrics.networkMetrics().networkInterfaceLoads().associateBy { it.name }
                metrics.networkMetrics().networkInterfaces().mapNotNull {
                    nicLoads[it.name]?.let { load ->
                        MonitorableItem(
                            id = it.name,
                            name = it.name,
                            description = it.mac,
                            maxValue = true.toConditionalValue(),
                            currentValue = load.isUp.toConditionalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.NETWORK_UPLOAD_RATE -> {
                val nicLoads = metrics.networkMetrics().networkInterfaceLoads().associateBy { it.name }
                metrics.networkMetrics().networkInterfaces().mapNotNull {
                    nicLoads[it.name]?.let { load ->
                        MonitorableItem(
                            id = it.name,
                            name = it.name,
                            description = it.mac,
                            maxValue = load.values.speed.toNumericalValue(),
                            currentValue = load.speed.sendBytesPerSecond.toNumericalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.NETWORK_DOWNLOAD_RATE -> {
                val nicLoads = metrics.networkMetrics().networkInterfaceLoads().associateBy { it.name }
                metrics.networkMetrics().networkInterfaces().mapNotNull {
                    nicLoads[it.name]?.let { load ->
                        MonitorableItem(
                            id = it.name,
                            name = it.name,
                            description = it.mac,
                            maxValue = load.values.speed.toNumericalValue(),
                            currentValue = load.speed.receiveBytesPerSecond.toNumericalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.CONTAINER_RUNNING -> {
                containerManager.containers().map {
                    MonitorableItem(
                        id = it.id,
                        name = it.names.joinToString(),
                        description = null,
                        maxValue = true.toConditionalValue(),
                        currentValue = (it.state == State.RUNNING).toConditionalValue(),
                        type = type
                    )
                }
            }

            Monitor.Type.CONTAINER_MEMORY_SPACE -> {
                val stats = containerManager.containerStats().associateBy { it.id }
                containerManager.containers().mapNotNull { container ->
                    stats[container.id]?.let { statistics ->
                        MonitorableItem(
                            id = statistics.id,
                            name = container.names.joinToString(),
                            description = container.image,
                            maxValue = statistics.memoryUsage.limitBytes.toNumericalValue(),
                            currentValue = statistics.memoryUsage.usageBytes.toNumericalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.CONTAINER_CPU_LOAD -> {
                val stats = containerManager.containerStats().associateBy { it.id }
                containerManager.containers().mapNotNull { container ->
                    stats[container.id]?.let { statistics ->
                        MonitorableItem(
                            id = statistics.id,
                            name = container.names.joinToString(),
                            description = container.image,
                            maxValue = (100f).toFractionalValue(),
                            currentValue = statistics.cpuUsage.usagePercentTotal.toFractionalValue(),
                            type = type
                        )
                    }
                }
            }

            Monitor.Type.PROCESS_MEMORY_SPACE -> {
                val memorySize = metrics.memoryMetrics().memoryInfo().totalBytes
                metrics.processesMetrics()
                    .processesInfo(ProcessSort.MEMORY, -1)
                    .processes
                    .map {
                        MonitorableItem(
                            id = it.processID.toString(),
                            name = it.name,
                            description = it.path,
                            maxValue = memorySize.toNumericalValue(),
                            currentValue = it.residentSetSize.toNumericalValue(),
                            type = type
                        )
                    }
            }

            Monitor.Type.PROCESS_CPU_LOAD -> {
                metrics.processesMetrics()
                    .processesInfo(ProcessSort.CPU, -1)
                    .processes
                    .map {
                        MonitorableItem(
                            id = it.processID.toString(),
                            name = it.name,
                            description = it.path,
                            maxValue = (100f).toFractionalValue(),
                            currentValue = it.cpuPercent.toFractionalValue(),
                            type = type
                        )
                    }
            }

            Monitor.Type.PROCESS_EXISTS -> {
                metrics.processesMetrics()
                    .processesInfo(ProcessSort.PID, -1)
                    .processes
                    .map {
                        MonitorableItem(
                            id = it.processID.toString(),
                            name = it.name,
                            description = it.path,
                            maxValue = true.toConditionalValue(),
                            currentValue = true.toConditionalValue(),
                            type = type
                        )
                    }
            }

            Monitor.Type.CONNECTIVITY -> {
                val connectivity = metrics.networkMetrics().connectivity()
                listOf(
                    MonitorableItem(
                        id = null,
                        name = connectivity.externalIp.orEmpty(),
                        description = null,
                        maxValue = true.toConditionalValue(),
                        currentValue = connectivity.connected.toConditionalValue(),
                        type = type
                    )
                )
            }

            Monitor.Type.EXTERNAL_IP_CHANGED -> {
                val connectivity = metrics.networkMetrics().connectivity()
                listOf(
                    MonitorableItem(
                        id = null,
                        name = connectivity.externalIp.orEmpty(),
                        description = null,
                        maxValue = false.toConditionalValue(),
                        currentValue = false.toConditionalValue(),
                        type = type
                    )
                )
            }

            Monitor.Type.WEBSERVER_UP -> {
                webServerCheckService.getAll().map {
                    MonitorableItem(
                        id = it.id.toString(),
                        name = it.url,
                        description = "Returns status 200 on a GET request",
                        maxValue = true.toConditionalValue(),
                        currentValue = (webServerCheckService.getStatusForWebServer(it.id)?.responseCode == 200).toConditionalValue(),
                        type = type
                    )
                }
            }

            Monitor.Type.DISK_TEMPERATURE -> {
                val diskLoads = metrics.diskMetrics().diskLoads().filter { it.temperature != null }.associateBy { it.name }
                metrics.diskMetrics().disks().mapNotNull {
                    diskLoads[it.name]?.let { load ->
                        MonitorableItem(
                            id = it.name,
                            name = it.name,
                            description = it.serial,
                            maxValue = MonitoredValue.NumericalValue(120),
                            currentValue = load.temperature?.toNumericalValue()?: MonitoredValue.NumericalValue(-1),
                            type = type
                        )
                    }
                }
            }
        }
    }
}