package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.Command
import com.krillsson.sysapi.core.domain.monitor.toConditionalValue
import com.krillsson.sysapi.core.domain.monitor.toFractionalValue
import com.krillsson.sysapi.core.domain.monitor.toNumericalValue
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.core.webservicecheck.AddWebServerResult
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckService
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.graphql.mutations.*
import com.krillsson.sysapi.windows.WindowsManager
import com.krillsson.sysapi.windows.services.WindowsServiceCommandResult
import com.krillsson.sysapi.core.pkill.ProcessKillerService
import com.krillsson.sysapi.systemd.CommandResult
import com.krillsson.sysapi.systemd.SystemDaemonManager
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import java.time.Duration

@Controller
class MutationResolver(
    private val monitorManager: MonitorManager,
    private val eventManager: EventManager,
    private val genericEventRepository: GenericEventRepository,
    private val containerService: ContainerService,
    private val systemDaemonManager: SystemDaemonManager,
    private val windowsManager: WindowsManager,
    private val processKiller: ProcessKillerService,
    private val webServerCheckService: WebServerCheckService
) {

    @MutationMapping
    fun performDockerContainerCommand(@Argument input: PerformDockerContainerCommandInput): PerformDockerContainerCommandOutput {
        val result = containerService.performCommandWithContainer(
            Command(input.containerId, input.command)
        )

        return when (result) {
            is com.krillsson.sysapi.docker.CommandResult.Failed -> PerformDockerContainerCommandOutputFailed(
                "Message: ${result.error.message ?: "Unknown reason"} Type: ${requireNotNull(result.error::class.simpleName)}",
            )

            com.krillsson.sysapi.docker.CommandResult.Success -> PerformDockerContainerCommandOutputSucceeded(input.containerId)
            com.krillsson.sysapi.docker.CommandResult.Unavailable -> PerformDockerContainerCommandOutputFailed("Docker client is unavailable")
        }
    }

    @MutationMapping
    fun performWindowsServiceCommand(@Argument input: PerformWindowsServiceCommandInput): PerformWindowsServiceCommandOutput {
        val result = windowsManager.performWindowsServiceCommand(input.serviceName, input.command)

        return when (result) {
            is WindowsServiceCommandResult.Failed -> PerformWindowsServiceCommandOutputFailed(
                "Message: ${result.error.message ?: "Unknown reason"} Type: ${requireNotNull(result.error::class.simpleName)}",
            )

            WindowsServiceCommandResult.Success -> PerformWindowsServiceCommandOutputSucceeded(input.serviceName)
            WindowsServiceCommandResult.Unavailable -> PerformWindowsServiceCommandOutputFailed("Windows service management is unavailable")
            WindowsServiceCommandResult.Disabled -> PerformWindowsServiceCommandOutputFailed("Windows service management is disabled in configuration.yml")
        }
    }

    @MutationMapping
    fun performSystemDaemonCommand(@Argument input: PerformSystemDaemonCommandInput): PerformSystemDaemonCommandOutput {
        val result = systemDaemonManager.performCommandWithService(
            input.serviceName, input.command
        )

        return when (result) {
            is CommandResult.Failed -> PerformSystemDaemonCommandOutputFailed(
                "Message: ${result.error.message ?: "Unknown reason"} Type: ${requireNotNull(result.error::class.simpleName)}",
            )

            CommandResult.Success -> PerformSystemDaemonCommandOutputSucceeded(input.serviceName)
            CommandResult.Unavailable -> PerformSystemDaemonCommandOutputFailed("SystemDaemon is unavailable")
            CommandResult.Disabled -> PerformSystemDaemonCommandOutputFailed("SystemDaemon service management is disabled in configuration.yml")
        }
    }

    @MutationMapping
    fun createNumericalValueMonitor(@Argument input: CreateNumericalMonitorInput): CreateMonitorOutput {
        val createdId = monitorManager.add(
            Duration.ofSeconds(input.inertiaInSeconds.toLong()),
            input.type,
            input.threshold.toNumericalValue(),
            input.monitoredItemId
        )
        return CreateMonitorOutput(createdId)
    }

    @MutationMapping
    fun createFractionalValueMonitor(@Argument input: CreateFractionMonitorInput): CreateMonitorOutput {
        val createdId = monitorManager.add(
            Duration.ofSeconds(input.inertiaInSeconds.toLong()),
            input.type,
            input.threshold.toFractionalValue(),
            input.monitoredItemId
        )
        return CreateMonitorOutput(createdId)
    }

    @MutationMapping
    fun createConditionalValueMonitor(@Argument input: CreateConditionalMonitorInput): CreateMonitorOutput {
        val createdId = monitorManager.add(
            Duration.ofSeconds(input.inertiaInSeconds.toLong()),
            input.type,
            input.threshold.toConditionalValue(),
            input.monitoredItemId
        )
        return CreateMonitorOutput(createdId)
    }

    @MutationMapping
    fun deleteMonitor(@Argument input: DeleteMonitorInput): DeleteMonitorOutput {
        val removed = monitorManager.remove(input.monitorId)
        return DeleteMonitorOutput(removed)
    }

    @MutationMapping
    fun updateNumericalValueMonitor(@Argument input: UpdateNumericalMonitorInput): UpdateMonitorOutput {
        return try {
            val updatedMonitorId = monitorManager.update(
                input.monitorId,
                input.inertiaInSeconds?.toLong()?.let { Duration.ofSeconds(it) },
                input.threshold?.toNumericalValue()
            )
            UpdateMonitorOutputSucceeded(updatedMonitorId)
        } catch (exception: Exception) {
            UpdateMonitorOutputFailed(exception.message ?: "Unknown reason")
        }
    }

    @MutationMapping
    fun updateFractionalValueMonitor(@Argument input: UpdateFractionMonitorInput): UpdateMonitorOutput {
        return try {
            val updatedMonitorId = monitorManager.update(
                input.monitorId,
                input.inertiaInSeconds?.toLong()?.let { Duration.ofSeconds(it) },
                input.threshold?.toFractionalValue()
            )
            UpdateMonitorOutputSucceeded(updatedMonitorId)
        } catch (exception: Exception) {
            UpdateMonitorOutputFailed(exception.message ?: "Unknown reason")
        }
    }

    @MutationMapping
    fun updateConditionalValueMonitor(@Argument input: UpdateConditionalMonitorInput): UpdateMonitorOutput {
        return try {
            val updatedMonitorId = monitorManager.update(
                input.monitorId,
                input.inertiaInSeconds?.toLong()?.let { Duration.ofSeconds(it) },
                input.threshold?.toConditionalValue()
            )
            UpdateMonitorOutputSucceeded(updatedMonitorId)
        } catch (exception: Exception) {
            UpdateMonitorOutputFailed(exception.message ?: "Unknown reason")
        }
    }

    @MutationMapping
    fun deleteEvent(@Argument input: DeleteEventInput): DeleteEventOutput {
        val removed = eventManager.remove(input.eventId)
        return DeleteEventOutput(removed)
    }

    @MutationMapping
    fun deleteGenericEvent(@Argument input: DeleteGenericEventInput): DeleteGenericEventOutput {
        val removed = genericEventRepository.removeById(input.eventId)
        return DeleteGenericEventOutput(removed)
    }

    @MutationMapping
    fun deleteEventsForMonitor(@Argument input: DeleteEventsForMonitorInput): DeleteEventOutput {
        val removed = eventManager.removeEventsForMonitorId(input.monitorId)
        return DeleteEventOutput(removed)
    }

    @MutationMapping
    fun deletePastEventsForMonitor(@Argument input: DeleteEventsForMonitorInput): DeleteEventOutput {
        val removed = eventManager.removePastEventsForMonitorId(input.monitorId)
        return DeleteEventOutput(removed)
    }

    @MutationMapping
    fun closeOngoingEventForMonitor(@Argument input: DeleteEventsForMonitorInput): DeleteEventOutput {
        val removed = eventManager.removeOngoingEventsForMonitorId(input.monitorId)
        return DeleteEventOutput(removed)
    }

    @MutationMapping
    fun addWebServerCheck(@Argument input: AddWebServerCheckInput): AddWebServerCheckOutput {
        return when (val result = webServerCheckService.addWebServer(input.url)) {
            is AddWebServerResult.Success -> AddWebServerCheckOutputSuccess(result.id)
            is AddWebServerResult.Fail -> AddWebServerCheckOutputFailed(result.reason)
        }
    }

    @MutationMapping
    fun deleteWebServerCheck(@Argument input: DeleteWebServerCheckInput): DeleteWebServerCheckOutput {
        val result = webServerCheckService.removeWebServerById(input.id)
        return DeleteWebServerCheckOutput(result)
    }

    @MutationMapping
    fun killProcess(@Argument pid: Int, @Argument forcibly: Boolean): ProcessKillerService.ProcessKillResult {
        return processKiller.kill(pid.toLong(), forcibly)
    }

}