package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.bash.SystemCtl
import com.krillsson.sysapi.systemd.SystemDaemonJournalEntry
import com.krillsson.sysapi.windows.eventlog.WindowsEventLogRecord
import com.krillsson.sysapi.windows.eventlog.WindowsEventLogSourceInfo
import com.krillsson.sysapi.windows.services.WindowsService
import reactor.core.publisher.Flux

interface SystemDaemonJournalAccess

interface SystemDaemonAccessAvailable : SystemDaemonJournalAccess {
    fun services(): List<SystemCtl.ListServicesOutput.Item>
    fun openJournal(name: String, limit: Int): List<SystemDaemonJournalEntry>

    fun openJournalConnection(
        name: String,
        after: String?,
        before: String?,
        first: Int?,
        last: Int?,
        reverse: Boolean?
    ): SystemDaemonJournalEntryConnection

    fun openAndTailJournal(
        name: String,
        startPosition: String?,
        reverse: Boolean?
    ): Flux<SystemDaemonJournalEntry>

    fun serviceDetails(name: String): SystemCtl.ServiceDetailsOutput?
}

data class SystemDaemonAccessUnavailable(
    val reason: String
) : SystemDaemonJournalAccess

interface WindowsManagementAccess

interface WindowsManagementAccessAvailable : WindowsManagementAccess {
    fun openEventLogBySource(source: String): List<WindowsEventLogRecord>
    fun openApplicationEventLog(): List<WindowsEventLogRecord>
    fun openSystemEventLog(): List<WindowsEventLogRecord>
    fun openSecurityEventLog(): List<WindowsEventLogRecord>
    fun eventLogs(): List<WindowsEventLogSourceInfo>
    fun services(): List<WindowsService>
    fun service(name: String): WindowsService?
    fun openApplicationEventLogConnection(after: String?, before: String?, first: Int?, last: Int?): WindowsEventLogRecordConnection
    fun openSystemEventLogConnection(after: String?, before: String?, first: Int?, last: Int?): WindowsEventLogRecordConnection
    fun openSecurityEventLogConnection(after: String?, before: String?, first: Int?, last: Int?): WindowsEventLogRecordConnection
    fun openEventLogBySourceConnection(source: String, after: String?, before: String?, first: Int?, last: Int?): WindowsEventLogRecordConnection
}

data class WindowsManagementAccessUnavailable(
    val reason: String
) : WindowsManagementAccess