package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.Command
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.monitoring.toEnumEntries
import com.krillsson.sysapi.core.monitoring.toEnumValueFromString
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import com.krillsson.sysapi.core.pkill.ProcessKillerService
import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.CheckService
import com.krillsson.sysapi.core.check.CreateCheckResult
import com.krillsson.sysapi.core.check.HttpCheckSpec
import com.krillsson.sysapi.core.check.HttpHeader
import com.krillsson.sysapi.core.check.DnsCheckSpec
import com.krillsson.sysapi.core.check.DnsRecordType
import com.krillsson.sysapi.core.check.HttpMethod
import com.krillsson.sysapi.core.check.PingCheckSpec
import com.krillsson.sysapi.core.check.RunCheckResult
import com.krillsson.sysapi.core.check.TcpCheckSpec
import com.krillsson.sysapi.core.check.UpdateCheckResult
import com.krillsson.sysapi.docker.ContainerBatchUpdateJobs
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.ContainerUpdateJobs
import com.krillsson.sysapi.graphql.mutations.*
import com.krillsson.sysapi.systemd.CommandResult
import com.krillsson.sysapi.systemd.SystemDaemonManager
import com.krillsson.sysapi.windows.WindowsManager
import com.krillsson.sysapi.windows.services.WindowsServiceCommandResult
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
    private val containerUpdateJobs: ContainerUpdateJobs,
    private val containerBatchUpdateJobs: ContainerBatchUpdateJobs,
    private val systemDaemonManager: SystemDaemonManager,
    private val windowsManager: WindowsManager,
    private val processKiller: ProcessKillerService,
    private val checkService: CheckService
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
    fun updateDockerContainer(@Argument input: UpdateDockerContainerInput): UpdateDockerContainerOutput {
        return when (val result = containerUpdateJobs.start(input.containerId, input.pullImage)) {
            is ContainerUpdateJobs.StartResult.Started -> UpdateDockerContainerOutputStarted(
                result.jobId,
                result.containerId
            )

            is ContainerUpdateJobs.StartResult.Rejected -> UpdateDockerContainerOutputFailed(result.reason)
        }
    }

    @MutationMapping
    fun updateDockerContainers(@Argument input: UpdateDockerContainersInput): UpdateDockerContainersOutput {
        return when (val result = containerBatchUpdateJobs.start(input.containerIds, input.pullImage)) {
            is ContainerBatchUpdateJobs.StartResult.Started -> UpdateDockerContainersOutputStarted(
                result.batchJobId,
                result.containerIds
            )

            is ContainerBatchUpdateJobs.StartResult.Rejected -> UpdateDockerContainersOutputFailed(result.reason)
        }
    }

    @MutationMapping
    fun abortDockerContainerBatchUpdate(@Argument input: AbortDockerContainerBatchUpdateInput): AbortDockerContainerBatchUpdateOutput {
        return when (val result = containerBatchUpdateJobs.abort(input.batchJobId)) {
            is ContainerBatchUpdateJobs.AbortResult.Accepted -> AbortDockerContainerBatchUpdateOutputAccepted(result.batchJobId)
            is ContainerBatchUpdateJobs.AbortResult.Rejected -> AbortDockerContainerBatchUpdateOutputFailed(result.reason)
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
    fun createEnumValueMonitor(@Argument input: CreateEnumMonitorInput): CreateMonitorOutput {
        val entries = input.type.toEnumEntries()
        val threshold = entries?.let { input.threshold.toEnumValueFromString(entries) }
        val created = threshold?.let {
            val createdId = monitorManager.add(
                Duration.ofSeconds(input.inertiaInSeconds.toLong()),
                input.type,
                threshold,
                input.monitoredItemId
            )
            CreateMonitorOutput(createdId)
        }
        return created
            ?: throw IllegalArgumentException("Unable to create monitor for ${input.type} with value ${input.monitoredItemId}")
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
    fun updateEnumValueMonitor(@Argument input: UpdateEnumMonitorInput): UpdateMonitorOutput {
        return try {
            val threshold = input.type.toEnumEntries()?.let { input.threshold?.toEnumValueFromString(it) }
            val updatedMonitorId = monitorManager.update(
                input.monitorId,
                input.inertiaInSeconds?.toLong()?.let { Duration.ofSeconds(it) },
                threshold
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
    fun createHttpCheck(@Argument input: CreateHttpCheckInput): CreateCheckOutput {
        return checkService.create(input.asSpec()).asOutput()
    }

    @MutationMapping
    fun updateHttpCheck(@Argument input: UpdateHttpCheckInput): UpdateCheckOutput {
        return checkService.update(input.id, input.asSpec()).asOutput()
    }

    @MutationMapping
    fun createTcpCheck(@Argument input: CreateTcpCheckInput): CreateCheckOutput {
        return checkService.create(input.asSpec()).asOutput()
    }

    @MutationMapping
    fun updateTcpCheck(@Argument input: UpdateTcpCheckInput): UpdateCheckOutput {
        return checkService.update(input.id, input.asSpec()).asOutput()
    }

    @MutationMapping
    fun createPingCheck(@Argument input: CreatePingCheckInput): CreateCheckOutput {
        return checkService.create(input.asSpec()).asOutput()
    }

    @MutationMapping
    fun updatePingCheck(@Argument input: UpdatePingCheckInput): UpdateCheckOutput {
        return checkService.update(input.id, input.asSpec()).asOutput()
    }

    @MutationMapping
    fun createDnsCheck(@Argument input: CreateDnsCheckInput): CreateCheckOutput {
        return checkService.create(input.asSpec()).asOutput()
    }

    @MutationMapping
    fun updateDnsCheck(@Argument input: UpdateDnsCheckInput): UpdateCheckOutput {
        return checkService.update(input.id, input.asSpec()).asOutput()
    }

    @MutationMapping
    fun deleteCheck(@Argument input: DeleteCheckInput): DeleteCheckOutput {
        return DeleteCheckOutput(checkService.delete(input.id))
    }

    @MutationMapping
    fun setCheckEnabled(@Argument input: SetCheckEnabledInput): UpdateCheckOutput {
        return checkService.setEnabled(input.id, input.enabled).asOutput()
    }

    @MutationMapping
    fun runCheckNow(@Argument input: RunCheckNowInput): RunCheckNowOutput {
        return when (val result = checkService.runNow(input.id)) {
            is RunCheckResult.Success -> RunCheckNowSuccess(result.result)
            is RunCheckResult.Fail -> RunCheckNowFailed(result.reason)
        }
    }

    @MutationMapping
    fun runOneOffHttpCheck(@Argument input: OneOffHttpCheckInput): CheckResult {
        return checkService.runOneOff(input.asSpec())
    }

    @MutationMapping
    fun runOneOffTcpCheck(@Argument input: OneOffTcpCheckInput): CheckResult {
        return checkService.runOneOff(input.asSpec())
    }

    @MutationMapping
    fun runOneOffPingCheck(@Argument input: OneOffPingCheckInput): CheckResult {
        return checkService.runOneOff(input.asSpec())
    }

    @MutationMapping
    fun runOneOffDnsCheck(@Argument input: OneOffDnsCheckInput): CheckResult {
        return checkService.runOneOff(input.asSpec())
    }

    @MutationMapping
    fun addWebServerCheck(@Argument input: AddWebServerCheckInput): AddWebServerCheckOutput {
        val spec = CreateHttpCheckInput(
            name = null,
            enabled = null,
            intervalSeconds = null,
            timeoutSeconds = null,
            url = input.url,
            method = null,
            expectedStatusCodes = null,
            keyword = null,
            keywordInverted = null,
            ignoreCertificateErrors = null,
            followRedirects = null,
            headers = null
        ).asSpec()
        return when (val result = checkService.create(spec)) {
            is CreateCheckResult.Success -> AddWebServerCheckOutputSuccess(result.id)
            is CreateCheckResult.Fail -> AddWebServerCheckOutputFailed(result.reason)
        }
    }

    @MutationMapping
    fun deleteWebServerCheck(@Argument input: DeleteWebServerCheckInput): DeleteWebServerCheckOutput {
        return DeleteWebServerCheckOutput(checkService.delete(input.id))
    }

    @MutationMapping
    fun killProcess(@Argument pid: Int, @Argument forcibly: Boolean): ProcessKillerService.ProcessKillResult {
        return processKiller.kill(pid.toLong(), forcibly)
    }

    private fun CreateHttpCheckInput.asSpec() = HttpCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        url = url,
        method = method ?: HttpMethod.GET,
        expectedStatusCodes = expectedStatusCodes ?: CheckService.DEFAULT_EXPECTED_STATUS_CODES,
        keyword = keyword,
        keywordInverted = keywordInverted ?: false,
        ignoreCertificateErrors = ignoreCertificateErrors ?: false,
        followRedirects = followRedirects ?: true,
        headers = headers.asDomain()
    )

    private fun UpdateHttpCheckInput.asSpec() = HttpCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        url = url,
        method = method ?: HttpMethod.GET,
        expectedStatusCodes = expectedStatusCodes ?: CheckService.DEFAULT_EXPECTED_STATUS_CODES,
        keyword = keyword,
        keywordInverted = keywordInverted ?: false,
        ignoreCertificateErrors = ignoreCertificateErrors ?: false,
        followRedirects = followRedirects ?: true,
        headers = headers.asDomain()
    )

    private fun OneOffHttpCheckInput.asSpec() = HttpCheckSpec(
        name = null,
        enabled = true,
        intervalSeconds = CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        url = url,
        method = method ?: HttpMethod.GET,
        expectedStatusCodes = expectedStatusCodes ?: CheckService.DEFAULT_EXPECTED_STATUS_CODES,
        keyword = keyword,
        keywordInverted = keywordInverted ?: false,
        ignoreCertificateErrors = ignoreCertificateErrors ?: false,
        followRedirects = followRedirects ?: true,
        headers = headers.asDomain()
    )

    private fun CreateTcpCheckInput.asSpec() = TcpCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        host = host,
        port = port
    )

    private fun UpdateTcpCheckInput.asSpec() = TcpCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        host = host,
        port = port
    )

    private fun OneOffTcpCheckInput.asSpec() = TcpCheckSpec(
        name = null,
        enabled = true,
        intervalSeconds = CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        host = host,
        port = port
    )

    private fun CreatePingCheckInput.asSpec() = PingCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        host = host
    )

    private fun UpdatePingCheckInput.asSpec() = PingCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        host = host
    )

    private fun OneOffPingCheckInput.asSpec() = PingCheckSpec(
        name = null,
        enabled = true,
        intervalSeconds = CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        host = host
    )

    private fun CreateDnsCheckInput.asSpec() = DnsCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        hostname = hostname,
        resolver = resolver,
        recordType = recordType ?: DnsRecordType.A,
        expectedValues = expectedValues.orEmpty()
    )

    private fun UpdateDnsCheckInput.asSpec() = DnsCheckSpec(
        name = name,
        enabled = enabled ?: true,
        intervalSeconds = intervalSeconds ?: CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        hostname = hostname,
        resolver = resolver,
        recordType = recordType ?: DnsRecordType.A,
        expectedValues = expectedValues.orEmpty()
    )

    private fun OneOffDnsCheckInput.asSpec() = DnsCheckSpec(
        name = null,
        enabled = true,
        intervalSeconds = CheckService.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds = timeoutSeconds ?: CheckService.DEFAULT_TIMEOUT_SECONDS,
        hostname = hostname,
        resolver = resolver,
        recordType = recordType ?: DnsRecordType.A,
        expectedValues = expectedValues.orEmpty()
    )

    private fun List<HttpHeaderInput>?.asDomain() = orEmpty().map { HttpHeader(it.name, it.value) }

    private fun CreateCheckResult.asOutput(): CreateCheckOutput = when (this) {
        is CreateCheckResult.Success -> CreateCheckSuccess(id)
        is CreateCheckResult.Fail -> CreateCheckFailed(reason)
    }

    private fun UpdateCheckResult.asOutput(): UpdateCheckOutput = when (this) {
        is UpdateCheckResult.Success -> UpdateCheckSuccess(id)
        is UpdateCheckResult.Fail -> UpdateCheckFailed(reason)
    }

}