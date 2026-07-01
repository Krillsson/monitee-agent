package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.cpu.CentralProcessor
import com.krillsson.sysapi.core.domain.gpu.Gpu
import com.krillsson.sysapi.core.domain.motherboard.Motherboard
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.processes.Process
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.graphql.domain.System
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import oshi.hardware.UsbDevice
import kotlin.jvm.optionals.getOrNull

@Controller
@SchemaMapping(typeName = "System")
class SystemResolver(val metrics: Metrics) {

    @SchemaMapping
    fun baseboard(system: System): Motherboard {
        return metrics.motherboardMetrics().motherboard()
    }

    @SchemaMapping
    fun hostname(system: System): String {
        return system.hostName
    }

    @SchemaMapping
    fun usbDevices(system: System): List<UsbDevice> {
        return baseboard(system).usbDevices.toList()
    }

    @SchemaMapping
    fun uptime(system: System): Long {
        return metrics.cpuMetrics().uptime()
    }

    @SchemaMapping
    fun connectivity(system: System): Connectivity {
        return metrics.networkMetrics().connectivity()
    }

    @SchemaMapping
    fun processor(system: System): CentralProcessor {
        return metrics.cpuMetrics().cpuInfo().centralProcessor
    }

    @SchemaMapping
    fun disks(system: System) = metrics.diskMetrics().disks()
    @SchemaMapping
    fun diskById(system: System, @Argument id: String) = metrics.diskMetrics().diskByName(id)
    @SchemaMapping
    fun fileSystems(system: System) = metrics.fileSystemMetrics().fileSystems()
    @SchemaMapping
    fun fileSystemById(system: System, @Argument id: String) = metrics.fileSystemMetrics().fileSystemById(id)

    @SchemaMapping
    fun processes(
            system: System,
            @Argument limit: Int = 0,
            @Argument sortBy: ProcessSort = ProcessSort.MEMORY
    ): List<Process> {
        return metrics.processesMetrics()
                .processesInfo(sortBy, limit).processes
    }

    @SchemaMapping
    fun processByPid(system: System,
                     @Argument pid: Int
    ) = metrics.processesMetrics().getProcessByPid(pid).getOrNull()

    @SchemaMapping
    fun networkInterfaces(system: System) = metrics.networkMetrics().networkInterfaces()
    @SchemaMapping
    fun networkInterfaceById(system: System, @Argument id: String) = metrics.networkMetrics().networkInterfaceById(id)
    @SchemaMapping
    fun gpus(system: System) = metrics.gpuMetrics().gpus()
    @SchemaMapping
    fun gpuById(system: System, @Argument id: String) = metrics.gpuMetrics().gpus().firstOrNull { it.id == id }
    @SchemaMapping
    fun memory(system: System) = metrics.memoryMetrics().memoryInfo()
}