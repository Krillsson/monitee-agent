package com.krillsson.sysapi.windows

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.graphql.domain.PageInfo
import com.krillsson.sysapi.graphql.domain.WindowsEventLogRecordConnection
import com.krillsson.sysapi.graphql.domain.WindowsEventLogRecordEdge
import com.krillsson.sysapi.graphql.domain.WindowsManagementAccessAvailable
import com.krillsson.sysapi.util.decodeAsInstantCursor
import com.krillsson.sysapi.util.encodeAsCursor
import com.krillsson.sysapi.windows.eventlog.WindowsEventLogReader
import com.krillsson.sysapi.windows.eventlog.WindowsEventLogRecord
import com.krillsson.sysapi.windows.services.WindowsService
import com.krillsson.sysapi.windows.services.WindowsServiceCommand
import com.krillsson.sysapi.windows.services.WindowsServiceCommandResult
import com.krillsson.sysapi.windows.services.WindowsServiceManager
import org.springframework.stereotype.Service
import oshi.SystemInfo
import oshi.util.PlatformEnum

@Service
class WindowsManager(config: YAMLConfigFile) : WindowsManagementAccessAvailable {
    fun supportedBySystem(): Boolean {
        return PlatformEnum.getCurrentPlatform() == PlatformEnum.WINDOWS || PlatformEnum.getCurrentPlatform() == PlatformEnum.WINDOWSCE
    }

    private val reader = WindowsEventLogReader(config.windows.eventLog)
    private val serviceManager = WindowsServiceManager(config.windows.serviceManagement)
    override fun openEventLogBySource(source: String) = reader.readAllBySource(source)
    override fun openApplicationEventLog(): List<WindowsEventLogRecord> {
        return reader.readAllApplication()
    }

    override fun openSystemEventLog(): List<WindowsEventLogRecord> {
        return reader.readAllSystem()
    }

    override fun openSecurityEventLog(): List<WindowsEventLogRecord> {
        return reader.readAllSecurity()
    }

    override fun openApplicationEventLogConnection(
        after: String?,
        before: String?,
        first: Int?,
        last: Int?
    ): WindowsEventLogRecordConnection {
        return reader.readAllApplication().asEventLogConnection(after, before, first, last)
    }

    override fun openSystemEventLogConnection(
        after: String?,
        before: String?,
        first: Int?,
        last: Int?
    ): WindowsEventLogRecordConnection {
        return reader.readAllSystem().asEventLogConnection(after, before, first, last)
    }

    override fun openSecurityEventLogConnection(
        after: String?,
        before: String?,
        first: Int?,
        last: Int?
    ): WindowsEventLogRecordConnection {
        return reader.readAllSecurity().asEventLogConnection(after, before, first, last)
    }

    override fun openEventLogBySourceConnection(
        source: String,
        after: String?,
        before: String?,
        first: Int?,
        last: Int?
    ): WindowsEventLogRecordConnection {
        return reader.readAllBySource(source).asEventLogConnection(after, before, first, last)
    }

    override fun eventLogs() = reader.allSources()
    override fun services(): List<WindowsService> {
        return serviceManager.services()
    }

    override fun service(name: String): WindowsService? {
        return serviceManager.service(name)
    }

    private fun List<WindowsEventLogRecord>.asEventLogConnection(
        after: String?,
        before: String?,
        first: Int?,
        last: Int?
    ): WindowsEventLogRecordConnection {
        val beforeTimestamp = before?.decodeAsInstantCursor()
        val afterTimestamp = after?.decodeAsInstantCursor()

        val filteredLogs = filter { log ->
            (beforeTimestamp == null || log.timestamp.isBefore(beforeTimestamp)) &&
                (afterTimestamp == null || log.timestamp.isAfter(afterTimestamp))
        }

        val paginatedLogs = if (first != null) {
            filteredLogs.take(first)
        } else if (last != null) {
            filteredLogs.takeLast(last)
        } else {
            filteredLogs.take(10) // Default page size.
        }

        val edges = paginatedLogs.map {
            WindowsEventLogRecordEdge(
                cursor = it.timestamp.encodeAsCursor(),
                node = it
            )
        }

        return WindowsEventLogRecordConnection(
            edges = edges,
            pageInfo = PageInfo(
                hasNextPage = filteredLogs.size > paginatedLogs.size,
                hasPreviousPage = before != null || after != null,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor
            )
        )
    }

    fun performWindowsServiceCommand(serviceName: String, command: WindowsServiceCommand): WindowsServiceCommandResult {
        return serviceManager.performWindowsServiceCommand(serviceName, command)
    }
}
