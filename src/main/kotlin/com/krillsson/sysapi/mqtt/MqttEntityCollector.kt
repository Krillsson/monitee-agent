package com.krillsson.sysapi.mqtt

import com.krillsson.sysapi.BuildConfig
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.Status
import com.krillsson.sysapi.docker.updates.ContainerUpdateChecker
import com.krillsson.sysapi.mqtt.MqttEntity.Component
import com.krillsson.sysapi.mqtt.MqttUnits.BYTES
import com.krillsson.sysapi.mqtt.MqttUnits.BYTES_PER_SECOND
import com.krillsson.sysapi.mqtt.MqttUnits.CELSIUS
import com.krillsson.sysapi.mqtt.MqttUnits.PERCENT
import com.krillsson.sysapi.mqtt.MqttUnits.SECONDS
import com.krillsson.sysapi.mqtt.MqttUnits.WATT
import com.krillsson.sysapi.smart.HealthStatus
import com.krillsson.sysapi.ups.UpsDevice
import com.krillsson.sysapi.ups.UpsService
import org.springframework.stereotype.Component as SpringComponent
import java.time.Clock
import java.time.Instant

@SpringComponent
class MqttEntityCollector(
    configFile: YAMLConfigFile,
    private val metrics: Metrics,
    private val monitorManager: MonitorManager,
    private val eventManager: EventManager,
    private val containerService: ContainerService,
    private val containerUpdateChecker: ContainerUpdateChecker,
    private val upsService: UpsService,
    private val clock: Clock
) {

    private val config = configFile.mqtt

    private var bootTime: Instant? = null

    private var systemInfo: SystemInfo? = null

    fun collect(): List<MeasuredEntity> {
        val entities = Entities()
        entities.cpu(metrics.cpuMetrics().cpuLoad())
        entities.memory(metrics.memoryMetrics().memoryLoad())
        entities.system(metrics.cpuMetrics().uptime(), metrics.networkMetrics().connectivity())
        entities.fileSystems(metrics.fileSystemMetrics().fileSystemLoads(), metrics.fileSystemMetrics().fileSystems())
        entities.disks(metrics.diskMetrics().diskLoads())
        entities.networkInterfaces(metrics.networkMetrics().networkInterfaceLoads())
        entities.gpus(metrics.gpuMetrics().gpuLoads())
        entities.containers()
        entities.upsDevices()
        entities.monitors()
        return entities.build()
    }

    private fun Entities.cpu(load: CpuLoad) {
        sensor("cpu_load", "CPU load", load.usagePercentage, unit = PERCENT, precision = 1)
        load.cpuHealth.temperatures.filter { it > 0 }.maxOrNull()?.let {
            sensor("cpu_temperature", "CPU temperature", it, unit = CELSIUS, deviceClass = "temperature", precision = 1)
        }
        sensor("cpu_processes", "Processes", load.processCount, diagnostic = true)
        sensor("cpu_threads", "Threads", load.threadCount, diagnostic = true)
        loadAverage("load_average_1m", "Load average 1m", load.loadAverages.oneMinute)
        loadAverage("load_average_5m", "Load average 5m", load.loadAverages.fiveMinutes)
        loadAverage("load_average_15m", "Load average 15m", load.loadAverages.fifteenMinutes)
    }

    private fun Entities.loadAverage(key: String, name: String, value: Double) {
        if (value >= 0) {
            sensor(key, name, value, precision = 2, diagnostic = true)
        }
    }

    private fun Entities.memory(memory: MemoryLoad) {
        sensor("memory_usage", "Memory usage", memory.usedPercent, unit = PERCENT, precision = 1)
        sensor("memory_used", "Memory used", memory.usedBytes, unit = BYTES, deviceClass = "data_size", precision = 0)
        sensor(
            key = "memory_total",
            name = "Memory total",
            value = memory.totalBytes,
            unit = BYTES,
            deviceClass = "data_size",
            stateClass = null,
            precision = 0,
            diagnostic = true
        )
        if (memory.swapTotalBytes > 0) {
            sensor(
                key = "swap_used",
                name = "Swap used",
                value = memory.swapUsedBytes,
                unit = BYTES,
                deviceClass = "data_size",
                precision = 0,
                diagnostic = true
            )
        }
    }

    private fun Entities.system(uptimeSeconds: Long, connectivity: Connectivity) {
        binarySensor("connectivity", "Internet", connectivity.connected, deviceClass = "connectivity")
        sensor(
            key = "boot_time",
            name = "Boot time",
            value = bootTime(uptimeSeconds).toString(),
            deviceClass = "timestamp",
            stateClass = null,
            diagnostic = true
        )
        connectivity.externalIp?.let { sensor("external_ip", "External IP", it, stateClass = null, diagnostic = true) }
        sensor("agent_version", "Agent version", BuildConfig.APP_VERSION, stateClass = null, diagnostic = true)
        with(systemInfo().operatingSystem) {
            sensor(
                key = "operating_system",
                name = "Operating system",
                value = listOf(family, versionInfo.version).filter { it.isNotBlank() }.joinToString(" "),
                stateClass = null,
                diagnostic = true
            )
        }
    }

    private fun Entities.fileSystems(loads: List<FileSystemLoad>, fileSystems: List<FileSystem>) {
        val byId = fileSystems.associateBy { it.id }
        MqttKeys.uniqueSlugs(loads) { if (it.name == "/") "root" else it.name }.forEach { (slug, load) ->
            val label = byId[load.id].displayName(load)
            if (load.totalSpaceBytes > 0) {
                val used = load.totalSpaceBytes - load.usableSpaceBytes
                sensor(
                    key = "fs_${slug}_usage",
                    name = "$label usage",
                    value = used * 100.0 / load.totalSpaceBytes,
                    unit = PERCENT,
                    precision = 1
                )
            }
            sensor(
                key = "fs_${slug}_free",
                name = "$label free",
                value = load.usableSpaceBytes,
                unit = BYTES,
                deviceClass = "data_size",
                precision = 0
            )
        }
    }

    private fun Entities.disks(loads: List<DiskLoad>) {
        MqttKeys.uniqueSlugs(loads) { it.name }.forEach { (slug, load) ->
            load.temperature?.let {
                sensor(
                    key = "disk_${slug}_temperature",
                    name = "${load.name} temperature",
                    value = it,
                    unit = CELSIUS,
                    deviceClass = "temperature",
                    precision = 0
                )
            }
            load.health?.let { health ->
                binarySensor(
                    key = "disk_${slug}_health",
                    name = "${load.name} health",
                    value = health.status != HealthStatus.HEALTHY,
                    deviceClass = "problem",
                    diagnostic = true,
                    attributes = mapOf(
                        "status" to health.status.name,
                        "messages" to health.messages
                    )
                )
            }
            sensor(
                key = "disk_${slug}_read_rate",
                name = "${load.name} read rate",
                value = load.speed.readBytesPerSecond.asRate(),
                unit = BYTES_PER_SECOND,
                deviceClass = "data_rate",
                precision = 0,
                diagnostic = true,
                enabledByDefault = false
            )
            sensor(
                key = "disk_${slug}_write_rate",
                name = "${load.name} write rate",
                value = load.speed.writeBytesPerSecond.asRate(),
                unit = BYTES_PER_SECOND,
                deviceClass = "data_rate",
                precision = 0,
                diagnostic = true,
                enabledByDefault = false
            )
        }
    }

    private fun Entities.networkInterfaces(loads: List<NetworkInterfaceLoad>) {
        if (!config.networkInterfaces) {
            return
        }
        MqttKeys.uniqueSlugs(loads.filter { it.isUp }) { it.name }.forEach { (slug, load) ->
            binarySensor(
                key = "net_${slug}_up",
                name = "${load.name} link",
                value = load.isUp,
                deviceClass = "connectivity",
                diagnostic = true,
                enabledByDefault = false
            )
            sensor(
                key = "net_${slug}_receive_rate",
                name = "${load.name} receive rate",
                value = load.speed.receiveBytesPerSecond.asRate(),
                unit = BYTES_PER_SECOND,
                deviceClass = "data_rate",
                precision = 0,
                diagnostic = true,
                enabledByDefault = false
            )
            sensor(
                key = "net_${slug}_send_rate",
                name = "${load.name} send rate",
                value = load.speed.sendBytesPerSecond.asRate(),
                unit = BYTES_PER_SECOND,
                deviceClass = "data_rate",
                precision = 0,
                diagnostic = true,
                enabledByDefault = false
            )
        }
    }

    private fun Entities.gpus(loads: List<GpuLoad>) {
        MqttKeys.uniqueSlugs(loads) { it.name }.forEach { (slug, load) ->
            sensor("gpu_${slug}_load", "${load.name} load", load.coreLoad, unit = PERCENT, precision = 1)
            if (load.health.temperature > 0) {
                sensor(
                    key = "gpu_${slug}_temperature",
                    name = "${load.name} temperature",
                    value = load.health.temperature,
                    unit = CELSIUS,
                    deviceClass = "temperature",
                    precision = 0
                )
            }
            if (load.vramTotalBytes > 0) {
                sensor(
                    key = "gpu_${slug}_vram_used",
                    name = "${load.name} VRAM used",
                    value = load.vramUsedBytes,
                    unit = BYTES,
                    deviceClass = "data_size",
                    precision = 0
                )
            }
        }
    }

    private fun Entities.containers() {
        if (containerService.status != Status.Available) {
            return
        }
        val containers = containerService.containers()
        sensor("containers_running", "Containers running", containers.count { it.state == State.RUNNING })
        sensor("container_updates", "Container updates", containerUpdateChecker.outdatedContainerIds().size)
        if (!config.containers) {
            return
        }
        MqttKeys.uniqueSlugs(containers) { it.displayName() }.forEach { (slug, container) ->
            binarySensor(
                key = "container_${slug}_running",
                name = container.displayName(),
                value = container.state == State.RUNNING,
                deviceClass = "running"
            )
        }
    }

    private fun Entities.upsDevices() {
        if (upsService.status() != UpsService.Status.Available) {
            return
        }
        val devices = upsService.upsDevices()
        val qualify = devices.size > 1
        MqttKeys.uniqueSlugs(devices) { it.name }.forEach { (slug, device) ->
            val label = if (qualify) device.name else "UPS"
            val metrics = device.metrics
            binarySensor(
                key = "ups_${slug}_status",
                name = "$label status",
                value = !metrics.isOperatingNormally(),
                deviceClass = "problem",
                attributes = mapOf("status" to metrics.upsStatus.map { it.name })
            )
            metrics.batteryMetrics?.chargePercent?.let {
                sensor("ups_${slug}_battery", "$label battery", it, unit = PERCENT, deviceClass = "battery")
            }
            metrics.batteryMetrics?.runtimeSeconds?.let {
                sensor("ups_${slug}_runtime", "$label runtime", it, unit = SECONDS, deviceClass = "duration")
            }
            metrics.loadPercent?.let { sensor("ups_${slug}_load", "$label load", it, unit = PERCENT, precision = 0) }
            metrics.realPowerLoadWatts?.let {
                sensor("ups_${slug}_power", "$label power", it, unit = WATT, deviceClass = "power")
            }
        }
    }

    private fun Entities.monitors() {
        val ongoing = eventManager.getAll()
            .filterIsInstance<OngoingEvent>()
            .associateBy { it.monitorId }
        monitorManager.getAll().forEach { monitor ->
            val event = ongoing[monitor.id]
            binarySensor(
                key = "monitor_${monitor.id.toString().replace("-", "_")}",
                name = MqttMonitorLabels.name(monitor.type, monitor.config.monitoredItemId),
                value = event != null,
                deviceClass = "problem",
                attributes = mapOf(
                    "monitor_id" to monitor.id.toString(),
                    "monitor_type" to monitor.type.name,
                    "monitored_item" to monitor.config.monitoredItemId,
                    "threshold" to monitor.config.threshold.plain(),
                    "value" to event?.value?.plain(),
                    "started_at" to event?.startTime?.toString()
                )
            )
        }
    }

    private fun bootTime(uptimeSeconds: Long): Instant {
        return bootTime ?: clock.instant().minusSeconds(uptimeSeconds).also { bootTime = it }
    }

    private fun systemInfo(): SystemInfo {
        return systemInfo ?: metrics.systemInfo().also { systemInfo = it }
    }

    private fun FileSystem?.displayName(load: FileSystemLoad): String {
        val candidate = listOfNotNull(this?.label, this?.mount, load.name)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return if (candidate == "/" || candidate.isBlank()) "Root" else candidate
    }

    private fun Container.displayName() = names.firstOrNull()?.trimStart('/')?.takeIf { it.isNotBlank() } ?: id.take(12)

    private fun Long.asRate() = takeIf { it >= 0 }

    private fun MonitoredValue.plain(): Any = when (this) {
        is MonitoredValue.NumericalValue -> value
        is MonitoredValue.FractionalValue -> value
        is MonitoredValue.ConditionalValue -> value
        is MonitoredValue.EnumValue<*> -> value.name
    }

    private class Entities {

        private val measured = mutableListOf<MeasuredEntity>()

        fun sensor(
            key: String,
            name: String,
            value: Any?,
            unit: String? = null,
            deviceClass: String? = null,
            stateClass: String? = "measurement",
            precision: Int? = null,
            diagnostic: Boolean = false,
            enabledByDefault: Boolean = true
        ) {
            measured += MeasuredEntity(
                entity = MqttEntity(
                    key = key,
                    name = name,
                    component = Component.SENSOR,
                    unit = unit,
                    deviceClass = deviceClass,
                    stateClass = stateClass,
                    precision = precision,
                    diagnostic = diagnostic,
                    enabledByDefault = enabledByDefault
                ),
                value = value
            )
        }

        fun binarySensor(
            key: String,
            name: String,
            value: Boolean?,
            deviceClass: String? = null,
            diagnostic: Boolean = false,
            enabledByDefault: Boolean = true,
            attributes: Map<String, Any?>? = null
        ) {
            measured += MeasuredEntity(
                entity = MqttEntity(
                    key = key,
                    name = name,
                    component = Component.BINARY_SENSOR,
                    deviceClass = deviceClass,
                    diagnostic = diagnostic,
                    enabledByDefault = enabledByDefault
                ),
                value = value?.let { if (it) ON else OFF },
                attributes = attributes
            )
        }

        fun build(): List<MeasuredEntity> = measured

        companion object {
            const val ON = "ON"
            const val OFF = "OFF"
        }
    }
}
