package com.krillsson.sysapi.bash

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dockerjava.api.exception.NotFoundException
import com.krillsson.sysapi.systemd.CommandResult
import com.krillsson.sysapi.systemd.SystemDaemonCommand
import com.krillsson.sysapi.util.logger

class SystemCtl(
    private val mapper: ObjectMapper
) {

    private val logger by logger()

    companion object {
        private const val LIST_SERVICES_COMMAND = "systemctl --output=json --type=service --all"
        private const val SERVICE_DETAILS_COMMAND = "systemctl show --no-pager"
    }

    class ListServicesOutput : ArrayList<ListServicesOutput.Item>() {
        data class Item(
            val active: String,
            val description: String,
            val load: String,
            val sub: String,
            val unit: String
        )
    }

    data class ServiceDetailsOutput(
        val active: String,
        val description: String,
        val load: String,
        val sub: String,
        val unit: String,
        val activeEnterPreformattedTimeStamp: String?,
        val activeExitPreformattedTimeStamp: String?,
        val names: List<String>?,
        val before: List<String>?,
        val after: List<String>?,
        val capabilities: List<String>?,
        val fragmentPath: String?,
        val statusText: String?,
        val startedAt: String?,
        val metrics: Metrics,
        val mainPid: Long?
    ) {
        data class Metrics(
            val memoryCurrentBytes: Long?,
            val cpuUsageNanoSeconds: Long?,
            val ioReadBytes: Long?,
            val ioWriteBytes: Long?,
        )
    }

    fun supportedBySystem(): Boolean {
        return (Bash.checkIfCommandExists("systemctl").getOrNull() ?: false) && services().isNotEmpty()
    }

    fun services(): List<ListServicesOutput.Item> {
        return try {
            val services = mutableListOf<ListServicesOutput.Item>()
            val result = Bash.executeToText(LIST_SERVICES_COMMAND)
            result.map { json ->
                val data = mapper.readValue(json, ListServicesOutput::class.java)
                data.forEach {
                    services.add(it)
                }
            }
            return services
        } catch (e: Exception) {
            logger.warn("Unable to execute command: ${e.message}")
            emptyList()
        }
    }

    private fun service(name: String): ListServicesOutput.Item? {
        return services().firstOrNull { it.unit == name }
    }

    fun serviceDetails(name: String): ServiceDetailsOutput? {
        return service(name)?.let {
            val result = Bash.executeToText("$SERVICE_DETAILS_COMMAND $name")
            result.mapCatching { data ->
                val values = parseDelimitedString(data)
                ServiceDetailsOutput(
                    active = it.active,
                    description = it.description,
                    load = it.load,
                    sub = it.sub,
                    unit = it.unit,
                    activeEnterPreformattedTimeStamp = values["ActiveEnterTimestamp"],
                    activeExitPreformattedTimeStamp = values["ActiveExitTimestamp"],
                    names = values["Names"]?.split(" "),
                    before = values["Before"]?.split(" "),
                    after = values["After"]?.split(" "),
                    capabilities = values["CapabilityBoundingSet"]?.split(" "),
                    fragmentPath = values["FragmentPath"],
                    statusText = values["StatusText"],
                    startedAt = values["ExecMainStartTimestampMonotonic"],
                    mainPid = values["ExecMainPID"]?.toLongOrNull(),
                    metrics = ServiceDetailsOutput.Metrics(
                        memoryCurrentBytes = values["MemoryCurrent"]?.toLongOrNull(),
                        cpuUsageNanoSeconds = values["CPUUsageNSec"]?.toLongOrNull(),
                        ioReadBytes = values["IOReadBytes"]?.toLongOrNull(),
                        ioWriteBytes = values["IOWriteBytes"]?.toLongOrNull(),
                    )
                )
            }.getOrNull()
        }
    }

    private fun parseDelimitedString(data: String) = data
        .lines()
        .map { line -> line.split("=") }
        .mapNotNull { if (it.size == 2) it[0] to it[1] else null }
        .toMap()

    fun performCommandWithService(serviceName: String, command: SystemDaemonCommand): CommandResult {
        return when {
            !hasUnit(serviceName) -> CommandResult.Failed(NotFoundException("$serviceName does not exist"))
            else -> {
                performCommand(serviceName, command)
            }
        }
    }

    private fun performCommand(serviceUnitName: String, command: SystemDaemonCommand): CommandResult {
        val exitStatus = Bash.executeToExitStatus("systemctl ${command.name.lowercase()} $serviceUnitName")
        return exitStatus.fold(
            onSuccess = {
                if (it == 0) {
                    CommandResult.Success
                } else {
                    CommandResult.Failed(RuntimeException("Failed with $it"))
                }
            },
            onFailure = {
                CommandResult.Failed(it)
            }
        )
    }

    private fun hasUnit(name: String): Boolean {
        return services().any { it.unit == name }
    }
}