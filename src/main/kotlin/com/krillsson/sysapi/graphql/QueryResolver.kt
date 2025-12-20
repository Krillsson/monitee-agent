package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.BuildConfig
import com.krillsson.sysapi.core.domain.event.Event
import com.krillsson.sysapi.core.domain.event.OngoingEvent
import com.krillsson.sysapi.core.domain.event.PastEvent
import com.krillsson.sysapi.core.domain.system.OperatingSystem
import com.krillsson.sysapi.core.domain.system.Platform
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.db.BasicHistorySystemLoadEntity
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.core.monitoring.event.EventManager
import com.krillsson.sysapi.core.monitoring.toEnumEntries
import com.krillsson.sysapi.core.webservicecheck.OneOffWebserverResult
import com.krillsson.sysapi.core.webservicecheck.WebServerCheck
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckHistoryEntry
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckService
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.Status
import com.krillsson.sysapi.graphql.domain.*
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.systemd.SystemDaemonManager
import com.krillsson.sysapi.ups.UpsService
import com.krillsson.sysapi.util.EnvironmentUtils
import com.krillsson.sysapi.windows.WindowsManager
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import oshi.software.os.OperatingSystem as OshiOsOperatingSystem

@Controller
class QueryResolver(
    private val monitorManager: MonitorManager,
    private val eventManager: EventManager,
    private val historyRepository: HistoryRepository,
    private val genericEventRepository: GenericEventRepository,
    private val operatingSystem: OperatingSystem,
    private val oshiOperatingSystem: OshiOsOperatingSystem,
    private val platform: Platform,
    private val containerManager: ContainerService,
    private val webServerCheckService: WebServerCheckService,
    private val windowsEventLogManager: WindowsManager,
    private val systemDaemonManager: SystemDaemonManager,
    private val serverIdService: ServerIdService,
    private val upsService: UpsService
) {

    @QueryMapping
    fun meta(): Meta = Meta(
        version = BuildConfig.APP_VERSION,
        buildDate = BuildConfig.BUILD_TIME.toString(),
        processId = oshiOperatingSystem.processId,
        serverId = serverIdService.serverId,
        endpoints = emptyList(),
    )

    @QueryMapping
    fun system(): System = System(EnvironmentUtils.hostName, operatingSystem, platform)

    @QueryMapping
    fun history(): List<BasicHistorySystemLoadEntity> {
        return historyRepository.getBasic()
    }

    @QueryMapping
    fun historyBetweenDates(
        @Argument from: OffsetDateTime,
        @Argument to: OffsetDateTime
    ): List<BasicHistorySystemLoadEntity> {
        return historyRepository.getHistoryLimitedToDates(from?.toInstant(), to?.toInstant())
    }

    @QueryMapping
    fun historyBetweenTimestamps(@Argument from: Instant, @Argument to: Instant): List<BasicHistorySystemLoadEntity> {
        return historyRepository.getHistoryLimitedToDates(from, to)
    }

    @QueryMapping
    fun monitors(): List<Monitor> {
        return monitorManager.getAll().map { it.asMonitor() }
    }

    @QueryMapping
    fun monitorById(@Argument id: String): Monitor? {
        return monitorManager.getById(UUID.fromString(id))?.asMonitor()
    }

    @QueryMapping
    fun monitorOfTypeByMonitoredItemId(
        @Argument type: com.krillsson.sysapi.core.monitoring.Monitor.Type,
        @Argument monitoredItemId: String
    ): Monitor? {
        return monitorManager.monitorOfTypeByMonitoredItemId(type, monitoredItemId).firstOrNull()?.asMonitor()
    }

    @QueryMapping
    fun events() = eventManager.getAll().toList()

    @QueryMapping
    fun monitorableItemsForType(@Argument input: MonitorableItemsInput): MonitorableItemsOutput {
        val items = monitorManager.getMonitorableItemForType(input.type).map {
            MonitorableItem(
                it.id,
                it.name,
                it.description,
                it.maxValue.asMonitoredValue(),
                it.currentValue.asMonitoredValue(),
                it.type
            )
        }
        return MonitorableItemsOutput(items)
    }

    @QueryMapping
    fun monitorableEnums(): List<MonitorableEnum> {
        return com.krillsson.sysapi.core.monitoring.Monitor.Type.entries
            .filter { it.valueType == com.krillsson.sysapi.core.monitoring.Monitor.ValueType.Enum }
            .mapNotNull { type ->
                val entries = type.toEnumEntries()
                entries?.let {
                    MonitorableEnum(
                        type,
                        entries.map {
                            EnumValue(
                                it.name,
                                it.ordinal
                            )
                        }
                    )
                }
            }
    }


    @QueryMapping
    fun eventById(@Argument id: String): Event? {
        return eventManager.getAll().firstOrNull { it.id == UUID.fromString(id) }
    }

    @QueryMapping
    fun genericEvents() = genericEventRepository.read()

    @QueryMapping
    fun pastEvents() = eventManager.getAll().filterIsInstance<PastEvent>()

    @QueryMapping
    fun ongoingEvents() = eventManager.getAll().filterIsInstance<OngoingEvent>()

    @QueryMapping
    fun webServerChecks(): List<WebServerCheck> = webServerCheckService.getAll()

    @QueryMapping
    fun webServerCheckById(@Argument id: UUID): WebServerCheck? = webServerCheckService.getById(id)

    @QueryMapping
    fun oneOffWebserverCheck(@Argument url: String): OneOffWebserverResult {
        return webServerCheckService.checkNow(url)
    }

    @QueryMapping
    fun runWebServerCheckNow(@Argument webserverCheckId: UUID): WebServerCheckHistoryEntry? {
        return webServerCheckService.runWebServerCheckNow(webserverCheckId)
    }

    @QueryMapping
    fun docker(): Docker {
        return when (val status = containerManager.status) {
            Status.Available -> DockerAvailable
            Status.Disabled -> DockerUnavailable(
                "The docker support is currently disabled. You can change this in configuration.yml",
                isDisabled = true
            )

            is Status.Unavailable -> DockerUnavailable(
                "${status.error.message ?: "Unknown reason"} Type: ${requireNotNull(status.error::class.simpleName)}",
                isDisabled = false
            )
        }
    }

    @QueryMapping
    fun upsInfo(): UpsInfo {
        return when (val status = upsService.status()) {
            UpsService.Status.Available -> UpsInfoAvailable
            UpsService.Status.Disabled -> UpsInfoUnavailable(
                "The UPS support is currently disabled. You can change this in the configuration.yml",
                isDisabled = true
            )

            is UpsService.Status.Unavailable -> UpsInfoUnavailable(
                "${status.error.message ?: "Unknown reason"} Type: ${requireNotNull(status.error::class.simpleName)}",
                isDisabled = true
            )
        }
    }

    @QueryMapping
    fun logFiles() = LogFileAccess

    @QueryMapping
    fun windowsManagement(): WindowsManagementAccess {
        return if (windowsEventLogManager.supportedBySystem()) {
            windowsEventLogManager
        } else {
            WindowsManagementAccessUnavailable(
                "Not supported by system"
            )
        }
    }

    @QueryMapping
    fun systemDaemon(): SystemDaemonJournalAccess {
        return when (val status = systemDaemonManager.status()) {
            SystemDaemonManager.Status.Available -> systemDaemonManager
            SystemDaemonManager.Status.Disabled -> SystemDaemonAccessUnavailable("Disabled")
            is SystemDaemonManager.Status.Unavailable -> SystemDaemonAccessUnavailable(status.error.message.orEmpty())
        }
    }


}