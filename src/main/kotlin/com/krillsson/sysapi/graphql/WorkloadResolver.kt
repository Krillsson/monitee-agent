package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.displayName
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.workload.PlatformAvailability
import com.krillsson.sysapi.workload.RunState
import com.krillsson.sysapi.workload.RunStates
import com.krillsson.sysapi.workload.WorkloadCatalog
import com.krillsson.sysapi.workload.WorkloadKind
import com.krillsson.sysapi.workload.WorkloadList
import com.krillsson.sysapi.workload.WorkloadPlatform
import com.krillsson.sysapi.workload.WorkloadUsage
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class WorkloadResolver(
    private val catalog: WorkloadCatalog,
    private val containerService: ContainerService,
    private val proxmoxService: ProxmoxService
) {

    @QueryMapping
    fun virtualMachines(): WorkloadList = catalog.list(WorkloadKind.VIRTUAL_MACHINE)

    @QueryMapping
    fun containers(): WorkloadList = catalog.list(WorkloadKind.CONTAINER)

    @SchemaMapping(typeName = "VirtualMachines", field = "all")
    fun allVirtualMachines(list: WorkloadList): List<Any> = list.all()

    @SchemaMapping(typeName = "VirtualMachines", field = "running")
    fun runningVirtualMachines(list: WorkloadList): List<Any> = list.running()

    @SchemaMapping(typeName = "VirtualMachines", field = "virtualMachine")
    fun virtualMachine(list: WorkloadList, @Argument platform: WorkloadPlatform, @Argument id: String): Any? =
        list.find(platform, id)

    @SchemaMapping(typeName = "VirtualMachines", field = "platforms")
    fun virtualMachinePlatforms(list: WorkloadList): List<PlatformAvailability> = list.platforms

    @SchemaMapping(typeName = "Containers", field = "all")
    fun allContainers(list: WorkloadList): List<Any> = list.all()

    @SchemaMapping(typeName = "Containers", field = "running")
    fun runningContainers(list: WorkloadList): List<Any> = list.running()

    @SchemaMapping(typeName = "Containers", field = "container")
    fun container(list: WorkloadList, @Argument platform: WorkloadPlatform, @Argument id: String): Any? =
        list.find(platform, id)

    @SchemaMapping(typeName = "Containers", field = "platforms")
    fun containerPlatforms(list: WorkloadList): List<PlatformAvailability> = list.platforms

    @SchemaMapping(typeName = "DockerContainer", field = "platform")
    fun dockerPlatform(container: Container): WorkloadPlatform = WorkloadPlatform.DOCKER

    @SchemaMapping(typeName = "DockerContainer", field = "name")
    fun dockerName(container: Container): String = container.displayName()

    @SchemaMapping(typeName = "DockerContainer", field = "runState")
    fun dockerRunState(container: Container): RunState = RunStates.docker(container.state)

    @SchemaMapping(typeName = "DockerContainer", field = "usage")
    fun dockerUsage(container: Container): WorkloadUsage? =
        containerService.lastSampledMetrics(container)?.let { sampled ->
            WorkloadUsage(
                cpuPercent = sampled.metrics.cpuUsage.usagePercentTotal,
                memoryUsedBytes = sampled.metrics.memoryUsage.usageBytes,
                memoryLimitBytes = sampled.metrics.memoryUsage.limitBytes.takeIf { it > 0 },
                sampledAt = sampled.sampledAt
            )
        }

    @SchemaMapping(typeName = "ProxmoxVm", field = "platform")
    fun proxmoxVmPlatform(guest: ProxmoxGuest): WorkloadPlatform = WorkloadPlatform.PROXMOX

    @SchemaMapping(typeName = "ProxmoxVm", field = "runState")
    fun proxmoxVmRunState(guest: ProxmoxGuest): RunState = RunStates.proxmox(guest.status)

    @SchemaMapping(typeName = "ProxmoxVm", field = "usage")
    fun proxmoxVmUsage(guest: ProxmoxGuest): WorkloadUsage? = proxmoxUsage(guest)

    @SchemaMapping(typeName = "ProxmoxContainer", field = "platform")
    fun proxmoxContainerPlatform(guest: ProxmoxGuest): WorkloadPlatform = WorkloadPlatform.PROXMOX

    @SchemaMapping(typeName = "ProxmoxContainer", field = "runState")
    fun proxmoxContainerRunState(guest: ProxmoxGuest): RunState = RunStates.proxmox(guest.status)

    @SchemaMapping(typeName = "ProxmoxContainer", field = "usage")
    fun proxmoxContainerUsage(guest: ProxmoxGuest): WorkloadUsage? = proxmoxUsage(guest)

    private fun proxmoxUsage(guest: ProxmoxGuest): WorkloadUsage? {
        val metrics = guest.metrics
        val sampledAt = proxmoxService.snapshot()?.lastSuccessfulPoll
        return when {
            metrics == null || sampledAt == null -> null
            else -> WorkloadUsage(
                cpuPercent = metrics.cpuUsage.usagePercentTotal,
                memoryUsedBytes = metrics.memoryUsage.usageBytes,
                memoryLimitBytes = metrics.memoryUsage.limitBytes,
                sampledAt = sampledAt
            )
        }
    }
}
