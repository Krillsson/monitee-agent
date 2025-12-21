package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.cpu.CpuInfo
import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.Disk
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterface
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.domain.processes.Process
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.domain.system.SystemLoad
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.monitoring.monitors.DiskSmartHealthMonitor
import com.krillsson.sysapi.core.webservicecheck.WebServerCheck
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckService
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.smart.HealthStatus
import com.krillsson.sysapi.ups.UpsDevice
import com.krillsson.sysapi.ups.UpsService
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.measureTimeMillis
import org.springframework.stereotype.Component
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Component
class MonitorInputCreator(
    private val metrics: Metrics,
    private val containerService: ContainerService,
    private val webServerCheckService: WebServerCheckService,
    private val upsService: UpsService,
) {

    companion object {
        const val THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND = 14_000L * 1024 * 1024
    }

    private val logger by logger()

    private val networkTypes = listOf(
        Monitor.Type.NETWORK_DOWNLOAD_RATE,
        Monitor.Type.NETWORK_UPLOAD_RATE,
        Monitor.Type.NETWORK_UP
    )
    private val diskTypes = listOf(
        Monitor.Type.DISK_READ_RATE,
        Monitor.Type.DISK_WRITE_RATE,
        Monitor.Type.DISK_TEMPERATURE,
        Monitor.Type.DISK_SMART_HEALTH,

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
            if (containerIds.isNotEmpty()) containerService.containersWithIds(containerIds) else emptyList()
        val containersStats = containerStatisticsIds.mapNotNull { id -> containerService.statsForContainer(id) }

        val upsDeviceMetrics = upsService.upsDevices().map { it.metrics }
        return MonitorInput(
            load,
            containers,
            containersStats,
            webserverChecks,
            upsDeviceMetrics
        )
    }

    fun getMonitorableItemForMonitor(monitor: Monitor<MonitoredValue>): MonitorableItem {
        val (duration, item) = measureTimeMillis {
            when (monitor.type) {
                Monitor.Type.FILE_SYSTEM_SPACE -> {
                    val fileSystemLoad =
                        metrics.fileSystemMetrics().fileSystemLoads().first { it.id == monitor.config.monitoredItemId }
                    val fileSystem =
                        metrics.fileSystemMetrics().fileSystems().first { it.id == monitor.config.monitoredItemId }
                    createFileSystemSpaceMonitorableItem(fileSystem, fileSystemLoad)
                }

                Monitor.Type.DISK_READ_RATE -> {
                    val diskLoad = metrics.diskMetrics().diskLoads().first { it.name == monitor.config.monitoredItemId }
                    val disk = metrics.diskMetrics().disks().first { it.name == monitor.config.monitoredItemId }
                    createDiskReadRateMonitorableItem(disk, diskLoad)
                }

                Monitor.Type.DISK_TEMPERATURE -> {
                    val diskLoad = metrics.diskMetrics().diskLoads().first { it.name == monitor.config.monitoredItemId }
                    val disk = metrics.diskMetrics().disks().first { it.name == monitor.config.monitoredItemId }
                    createDiskTemperatureMonitorableItem(disk, diskLoad)
                }

                Monitor.Type.DISK_WRITE_RATE -> {
                    val diskLoad = metrics.diskMetrics().diskLoads().first { it.name == monitor.config.monitoredItemId }
                    val disk = metrics.diskMetrics().disks().first { it.name == monitor.config.monitoredItemId }
                    createDiskWriteRateMonitorableItem(disk, diskLoad)
                }

                Monitor.Type.NETWORK_UP -> {
                    val nicLoad =
                        metrics.networkMetrics().networkInterfaceLoads()
                            .first { it.name == monitor.config.monitoredItemId }
                    val nic =
                        metrics.networkMetrics().networkInterfaces().first { it.name == monitor.config.monitoredItemId }
                    createNetworkUpMonitorableItem(nic, nicLoad)
                }

                Monitor.Type.NETWORK_UPLOAD_RATE -> {
                    val nicLoad =
                        metrics.networkMetrics().networkInterfaceLoads()
                            .first { it.name == monitor.config.monitoredItemId }
                    val nic =
                        metrics.networkMetrics().networkInterfaces().first { it.name == monitor.config.monitoredItemId }
                    createNetworkUploadRateMonitorableItem(nic, nicLoad)
                }

                Monitor.Type.NETWORK_DOWNLOAD_RATE -> {
                    val nicLoad =
                        metrics.networkMetrics().networkInterfaceLoads()
                            .first { it.name == monitor.config.monitoredItemId }
                    val nic =
                        metrics.networkMetrics().networkInterfaces().first { it.name == monitor.config.monitoredItemId }
                    createNetworkDownloadRateMonitorableItem(nic, nicLoad)
                }

                Monitor.Type.CONTAINER_RUNNING -> {
                    val container =
                        requireNotNull(containerService.container(requireNotNull(monitor.config.monitoredItemId)))
                    createContainerRunningMonitorableItem(container)
                }

                Monitor.Type.CONTAINER_MEMORY_SPACE -> {
                    val container =
                        requireNotNull(containerService.container(requireNotNull(monitor.config.monitoredItemId)))
                    val containerStats =
                        requireNotNull(containerService.statsForContainer(requireNotNull(monitor.config.monitoredItemId)))
                    createContainerMemorySpaceMonitorableItem(containerStats, container)
                }

                Monitor.Type.CONTAINER_CPU_LOAD -> {
                    val container =
                        requireNotNull(containerService.container(requireNotNull(monitor.config.monitoredItemId)))
                    val containerStats =
                        requireNotNull(containerService.statsForContainer(requireNotNull(monitor.config.monitoredItemId)))
                    createContainerCpuLoadMonitorableItem(containerStats, container)
                }

                Monitor.Type.PROCESS_MEMORY_SPACE -> {
                    val process = requireNotNull(
                        metrics.processesMetrics().getProcessByPid(monitor.config.monitoredItemId?.toInt() ?: 0)
                            .getOrNull()
                    )
                    val memorySize = metrics.memoryMetrics().memoryInfo().totalBytes
                    createProcessMemorySpaceMonitorableItem(process, memorySize)
                }

                Monitor.Type.PROCESS_CPU_LOAD -> {
                    val process = requireNotNull(
                        metrics.processesMetrics().getProcessByPid(monitor.config.monitoredItemId?.toInt() ?: 0)
                            .getOrNull()
                    )
                    createProcessCpuLoadMonitorableItem(process)
                }

                Monitor.Type.PROCESS_EXISTS -> {
                    val process = requireNotNull(
                        metrics.processesMetrics().getProcessByPid(monitor.config.monitoredItemId?.toInt() ?: 0)
                            .getOrNull()
                    )
                    createProcessExistsMonitorableItem(process)
                }

                Monitor.Type.WEBSERVER_UP -> {
                    val webServerCheck =
                        requireNotNull(webServerCheckService.getById(UUID.fromString(requireNotNull(monitor.config.monitoredItemId))))
                    createWebserverUpMonitorableItem(webServerCheck)
                }

                Monitor.Type.EXTERNAL_IP_CHANGED -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.CPU_LOAD -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.CPU_TEMP -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.MEMORY_SPACE -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.MEMORY_USED -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.CONNECTIVITY -> getMonitorableItemForType(monitor.type).first()
                Monitor.Type.UPS_OPERATING_NORMALLY -> {
                    val device = upsService.upsDevices().first { it.id == monitor.config.monitoredItemId }
                    createMonitorIsOperatingNormallyItem(device)
                }
                Monitor.Type.UPS_LOAD_PERCENTAGE -> {
                    val device = upsService.upsDevices().first { it.id == monitor.config.monitoredItemId }
                    createUpsLoadPercentageMonitoredItem(device)
                }
                Monitor.Type.DISK_SMART_HEALTH -> {
                    val diskLoad = metrics.diskMetrics().diskLoads().first { it.name == monitor.config.monitoredItemId }
                    val disk = metrics.diskMetrics().disks().first { it.name == monitor.config.monitoredItemId }
                    createDiskSmartHealthMonitorableItem(disk, diskLoad)
                }
            }
        }
        logger.debug(
            "Took {} to fetch monitorable item for monitor {}",
            "${duration.toInt()}ms",
            monitor
        )
        return item
    }

    fun getMonitorableItemForType(type: Monitor.Type): List<MonitorableItem> {
        return when (type) {
            Monitor.Type.LOAD_AVERAGE_ONE_MINUTE -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    monitorableItemLoadAverage(cpuInfo, cpuMetrics)
                )
            }

            Monitor.Type.CPU_LOAD -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    createCpuLoadMonitorableItem(cpuInfo, cpuMetrics)
                )
            }

            Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    createMonitorableLoadAverageFiveMinItem(cpuInfo, cpuMetrics)
                )
            }

            Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    createMonitorableLoadAverageFifteenMinItem(cpuInfo, cpuMetrics)
                )
            }

            Monitor.Type.CPU_TEMP -> {
                val cpuInfo = metrics.cpuMetrics().cpuInfo()
                val cpuMetrics = metrics.cpuMetrics().cpuLoad()
                listOf(
                    createMonitorableCpuTempItem(cpuInfo, cpuMetrics)
                )
            }

            Monitor.Type.FILE_SYSTEM_SPACE -> {
                val fileSystemLoads = metrics.fileSystemMetrics().fileSystemLoads().associateBy { it.id }
                metrics.fileSystemMetrics().fileSystems().mapNotNull {
                    fileSystemLoads[it.id]?.let { load ->
                        createFileSystemSpaceMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.DISK_READ_RATE -> {
                val diskLoads = metrics.diskMetrics().diskLoads().associateBy { it.name }
                metrics.diskMetrics().disks().mapNotNull {
                    diskLoads[it.name]?.let { load ->
                        createDiskReadRateMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.DISK_WRITE_RATE -> {
                val diskLoads = metrics.diskMetrics().diskLoads().associateBy { it.name }
                metrics.diskMetrics().disks().mapNotNull {
                    diskLoads[it.name]?.let { load ->
                        createDiskWriteRateMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.MEMORY_SPACE -> {
                val load = metrics.memoryMetrics().memoryLoad()
                listOf(
                    createMemorySpaceMonitorableItem(load)
                )
            }

            Monitor.Type.MEMORY_USED -> {
                val load = metrics.memoryMetrics().memoryLoad()
                listOf(
                    createMemoryUsedMonitorableItem(load)
                )
            }

            Monitor.Type.NETWORK_UP -> {
                val nicLoads = metrics.networkMetrics().networkInterfaceLoads().associateBy { it.name }
                metrics.networkMetrics().networkInterfaces().mapNotNull {
                    nicLoads[it.name]?.let { load ->
                        createNetworkUpMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.NETWORK_UPLOAD_RATE -> {
                val nicLoads = metrics.networkMetrics().networkInterfaceLoads().associateBy { it.name }
                metrics.networkMetrics().networkInterfaces().mapNotNull {
                    nicLoads[it.name]?.let { load ->
                        createNetworkUploadRateMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.NETWORK_DOWNLOAD_RATE -> {
                val nicLoads = metrics.networkMetrics().networkInterfaceLoads().associateBy { it.name }
                metrics.networkMetrics().networkInterfaces().mapNotNull {
                    nicLoads[it.name]?.let { load ->
                        createNetworkDownloadRateMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.CONTAINER_RUNNING -> {
                containerService.containers().map {
                    createContainerRunningMonitorableItem(it)
                }
            }

            Monitor.Type.CONTAINER_MEMORY_SPACE -> {
                val stats = containerService.containerStats().associateBy { it.id }
                containerService.containers().mapNotNull { container ->
                    stats[container.id]?.let { statistics ->
                        createContainerMemorySpaceMonitorableItem(statistics, container)
                    }
                }
            }

            Monitor.Type.CONTAINER_CPU_LOAD -> {
                val stats = containerService.containerStats().associateBy { it.id }
                containerService.containers().mapNotNull { container ->
                    stats[container.id]?.let { statistics ->
                        createContainerCpuLoadMonitorableItem(statistics, container)
                    }
                }
            }

            Monitor.Type.PROCESS_MEMORY_SPACE -> {
                val memorySize = metrics.memoryMetrics().memoryInfo().totalBytes
                metrics.processesMetrics()
                    .processesInfo(ProcessSort.MEMORY, -1)
                    .processes
                    .map {
                        createProcessMemorySpaceMonitorableItem(it, memorySize)
                    }
            }

            Monitor.Type.PROCESS_CPU_LOAD -> {
                metrics.processesMetrics()
                    .processesInfo(ProcessSort.CPU, -1)
                    .processes
                    .map {
                        createProcessCpuLoadMonitorableItem(it)
                    }
            }

            Monitor.Type.PROCESS_EXISTS -> {
                metrics.processesMetrics()
                    .processesInfo(ProcessSort.PID, -1)
                    .processes
                    .map {
                        createProcessExistsMonitorableItem(it)
                    }
            }

            Monitor.Type.CONNECTIVITY -> {
                val connectivity = metrics.networkMetrics().connectivity()
                listOf(
                    createConnectivityMonitorableItem(connectivity)
                )
            }

            Monitor.Type.EXTERNAL_IP_CHANGED -> {
                val connectivity = metrics.networkMetrics().connectivity()
                listOf(
                    createExternalIpChangeMonitorableItem(connectivity)
                )
            }

            Monitor.Type.WEBSERVER_UP -> {
                webServerCheckService.getAll().map {
                    createWebserverUpMonitorableItem(it)
                }
            }

            Monitor.Type.DISK_TEMPERATURE -> {
                val diskLoads =
                    metrics.diskMetrics().diskLoads().filter { it.temperature != null }.associateBy { it.name }
                metrics.diskMetrics().disks().mapNotNull {
                    diskLoads[it.name]?.let { load ->
                        createDiskTemperatureMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.DISK_SMART_HEALTH ->  {
                val diskLoads = metrics.diskMetrics().diskLoads().associateBy { it.name }
                metrics.diskMetrics().disks().mapNotNull {
                    diskLoads[it.name]?.let { load ->
                        createDiskSmartHealthMonitorableItem(it, load)
                    }
                }
            }

            Monitor.Type.UPS_OPERATING_NORMALLY -> upsService.upsDevices().map {
                createMonitorIsOperatingNormallyItem(it)
            }
            Monitor.Type.UPS_LOAD_PERCENTAGE -> upsService.upsDevices().map {
                createUpsLoadPercentageMonitoredItem(it)
            }
        }
    }

    private fun createMonitorIsOperatingNormallyItem(device: UpsDevice): MonitorableItem = MonitorableItem(
        id = device.id,
        name = device.name,
        description = "${device.name} is operating normally",
        maxValue = true.toConditionalValue(),
        currentValue = device.metrics.isOperatingNormally()
            .toConditionalValue(),
        type = Monitor.Type.UPS_OPERATING_NORMALLY
    )

    private fun createUpsLoadPercentageMonitoredItem(device: UpsDevice): MonitorableItem = MonitorableItem(
        id = device.id,
        name = device.name,
        description = "${device.name} is operating normally",
        maxValue = MonitoredValue.NumericalValue(100),
        currentValue = device.metrics.loadPercent?.toNumericalValue() ?: MonitoredValue.NumericalValue(-1),
        type = Monitor.Type.UPS_LOAD_PERCENTAGE
    )

    private fun createDiskTemperatureMonitorableItem(
        it: Disk,
        load: DiskLoad
    ) = MonitorableItem(
        id = it.name,
        name = it.name,
        description = it.serial,
        maxValue = MonitoredValue.NumericalValue(120),
        currentValue = load.temperature?.toNumericalValue() ?: MonitoredValue.NumericalValue(-1),
        type = Monitor.Type.DISK_TEMPERATURE
    )

    private fun createDiskSmartHealthMonitorableItem(
        it: Disk,
        load: DiskLoad
    ) = MonitorableItem(
        id = it.name,
        name = it.name,
        description = it.serial,
        maxValue = HealthStatus.CRITICAL.toEnumValue(),
        currentValue = load.health?.status?.toEnumValue() ?: HealthStatus.HEALTHY.toEnumValue(),
        type = Monitor.Type.DISK_SMART_HEALTH
    )

    private fun createWebserverUpMonitorableItem(it: WebServerCheck) = MonitorableItem(
        id = it.id.toString(),
        name = it.url,
        description = "Returns status 200 on a GET request",
        maxValue = true.toConditionalValue(),
        currentValue = (webServerCheckService.getStatusForWebServer(it.id)?.responseCode == 200).toConditionalValue(),
        type = Monitor.Type.WEBSERVER_UP
    )

    private fun createExternalIpChangeMonitorableItem(connectivity: Connectivity) = MonitorableItem(
        id = null,
        name = connectivity.externalIp.orEmpty(),
        description = null,
        maxValue = false.toConditionalValue(),
        currentValue = false.toConditionalValue(),
        type = Monitor.Type.EXTERNAL_IP_CHANGED
    )

    private fun createConnectivityMonitorableItem(connectivity: Connectivity) = MonitorableItem(
        id = null,
        name = connectivity.externalIp.orEmpty(),
        description = null,
        maxValue = true.toConditionalValue(),
        currentValue = connectivity.connected.toConditionalValue(),
        type = Monitor.Type.CONNECTIVITY
    )

    private fun createProcessExistsMonitorableItem(it: Process) = MonitorableItem(
        id = it.processID.toString(),
        name = it.name,
        description = it.path,
        maxValue = true.toConditionalValue(),
        currentValue = true.toConditionalValue(),
        type = Monitor.Type.PROCESS_EXISTS
    )

    private fun createProcessCpuLoadMonitorableItem(it: Process) = MonitorableItem(
        id = it.processID.toString(),
        name = it.name,
        description = it.path,
        maxValue = (100f).toFractionalValue(),
        currentValue = it.cpuPercent.toFractionalValue(),
        type = Monitor.Type.PROCESS_CPU_LOAD
    )

    private fun createProcessMemorySpaceMonitorableItem(
        it: Process,
        memorySize: Long
    ) = MonitorableItem(
        id = it.processID.toString(),
        name = it.name,
        description = it.path,
        maxValue = memorySize.toNumericalValue(),
        currentValue = it.residentSetSize.toNumericalValue(),
        type = Monitor.Type.PROCESS_MEMORY_SPACE
    )

    private fun createContainerCpuLoadMonitorableItem(
        statistics: ContainerMetrics,
        container: Container
    ) = MonitorableItem(
        id = statistics.id,
        name = container.names.joinToString(),
        description = container.image,
        maxValue = (100f).toFractionalValue(),
        currentValue = statistics.cpuUsage.usagePercentTotal.toFractionalValue(),
        type = Monitor.Type.CONTAINER_CPU_LOAD
    )

    private fun createContainerMemorySpaceMonitorableItem(
        statistics: ContainerMetrics,
        container: Container
    ) = MonitorableItem(
        id = statistics.id,
        name = container.names.joinToString(),
        description = container.image,
        maxValue = statistics.memoryUsage.limitBytes.toNumericalValue(),
        currentValue = statistics.memoryUsage.usageBytes.toNumericalValue(),
        type = Monitor.Type.CONTAINER_MEMORY_SPACE
    )

    private fun createContainerRunningMonitorableItem(it: Container) = MonitorableItem(
        id = it.id,
        name = it.names.joinToString(),
        description = null,
        maxValue = true.toConditionalValue(),
        currentValue = (it.state == State.RUNNING).toConditionalValue(),
        type = Monitor.Type.CONTAINER_RUNNING
    )

    private fun createNetworkDownloadRateMonitorableItem(
        it: NetworkInterface,
        load: NetworkInterfaceLoad
    ) = MonitorableItem(
        id = it.name,
        name = it.name,
        description = it.ipv4.firstOrNull() ?: it.ipv6.firstOrNull() ?: it.mac,
        maxValue = load.values.speed.toNumericalValue(),
        currentValue = load.speed.receiveBytesPerSecond.toNumericalValue(),
        type = Monitor.Type.NETWORK_DOWNLOAD_RATE
    )

    private fun createNetworkUploadRateMonitorableItem(
        it: NetworkInterface,
        load: NetworkInterfaceLoad
    ) = MonitorableItem(
        id = it.name,
        name = it.name,
        description = it.ipv4.firstOrNull() ?: it.ipv6.firstOrNull() ?: it.mac,
        maxValue = load.values.speed.toNumericalValue(),
        currentValue = load.speed.sendBytesPerSecond.toNumericalValue(),
        type = Monitor.Type.NETWORK_UPLOAD_RATE
    )

    private fun createNetworkUpMonitorableItem(
        it: NetworkInterface,
        load: NetworkInterfaceLoad
    ) = MonitorableItem(
        id = it.name,
        name = it.name,
        description = it.ipv4.firstOrNull() ?: it.ipv6.firstOrNull() ?: it.mac,
        maxValue = true.toConditionalValue(),
        currentValue = load.isUp.toConditionalValue(),
        type = Monitor.Type.NETWORK_UP
    )

    private fun createMemoryUsedMonitorableItem(load: MemoryLoad) = MonitorableItem(
        id = null,
        name = "Memory space used",
        description = null,
        maxValue = load.totalBytes.toNumericalValue(),
        currentValue = load.usedBytes.toNumericalValue(),
        type = Monitor.Type.MEMORY_USED
    )

    private fun createMemorySpaceMonitorableItem(
        load: MemoryLoad,
    ) = MonitorableItem(
        id = null,
        name = "Memory space available",
        description = null,
        maxValue = load.totalBytes.toNumericalValue(),
        currentValue = load.availableBytes.toNumericalValue(),
        type = Monitor.Type.MEMORY_SPACE
    )

    private fun createDiskWriteRateMonitorableItem(
        it: Disk,
        load: DiskLoad
    ) = MonitorableItem(
        id = it.name,
        name = it.name,
        description = it.serial,
        maxValue = THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND.toNumericalValue(),
        currentValue = load.speed.writeBytesPerSecond.toNumericalValue(),
        type = Monitor.Type.DISK_WRITE_RATE
    )

    private fun createDiskReadRateMonitorableItem(
        it: Disk,
        load: DiskLoad
    ) = MonitorableItem(
        id = it.name,
        name = it.name,
        description = it.serial,
        maxValue = THEORETICAL_DRIVE_READ_SPEED_LIMIT_BYTES_PER_SECOND.toNumericalValue(),
        currentValue = load.speed.readBytesPerSecond.toNumericalValue(),
        type = Monitor.Type.DISK_READ_RATE
    )

    private fun createFileSystemSpaceMonitorableItem(
        it: FileSystem,
        load: FileSystemLoad
    ) = MonitorableItem(
        id = it.id,
        name = it.mount,
        description = it.description,
        maxValue = load.totalSpaceBytes.toNumericalValue(),
        currentValue = load.usableSpaceBytes.toNumericalValue(),
        type = Monitor.Type.FILE_SYSTEM_SPACE
    )

    private fun createMonitorableCpuTempItem(
        cpuInfo: CpuInfo,
        cpuMetrics: CpuLoad
    ) = MonitorableItem(
        null,
        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
        null,
        MonitoredValue.NumericalValue(120),
        cpuMetrics.cpuHealth.temperatures.average().toNumericalValue(),
        Monitor.Type.CPU_TEMP
    )

    private fun createMonitorableLoadAverageFifteenMinItem(
        cpuInfo: CpuInfo,
        cpuMetrics: CpuLoad
    ) = MonitorableItem(
        null,
        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
        null,
        cpuInfo.centralProcessor.logicalProcessorCount.toFloat().toFractionalValue(),
        cpuMetrics.loadAverages.fifteenMinutes.toFractionalValue(),
        Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES
    )

    private fun createMonitorableLoadAverageFiveMinItem(
        cpuInfo: CpuInfo,
        cpuMetrics: CpuLoad
    ) = MonitorableItem(
        null,
        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
        null,
        cpuInfo.centralProcessor.logicalProcessorCount.toFloat().toFractionalValue(),
        cpuMetrics.loadAverages.fiveMinutes.toFractionalValue(),
        Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES
    )

    private fun createCpuLoadMonitorableItem(
        cpuInfo: CpuInfo,
        cpuMetrics: CpuLoad
    ) = MonitorableItem(
        null,
        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
        null,
        100F.toFractionalValue(),
        cpuMetrics.usagePercentage.toFractionalValue(),
        Monitor.Type.CPU_LOAD
    )

    private fun monitorableItemLoadAverage(
        cpuInfo: CpuInfo,
        cpuMetrics: CpuLoad,
    ) = MonitorableItem(
        null,
        cpuInfo.centralProcessor.name ?: cpuInfo.centralProcessor.model ?: "",
        null,
        cpuInfo.centralProcessor.logicalProcessorCount.toFloat().toFractionalValue(),
        cpuMetrics.loadAverages.oneMinute.toFractionalValue(),
        Monitor.Type.LOAD_AVERAGE_ONE_MINUTE
    )
}