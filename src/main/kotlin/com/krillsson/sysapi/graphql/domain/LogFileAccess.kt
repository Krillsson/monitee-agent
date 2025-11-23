package com.krillsson.sysapi.graphql.domain

import com.krillsson.sysapi.docker.DockerLogMessage
import com.krillsson.sysapi.logaccess.LogMessage
import com.krillsson.sysapi.systemd.SystemDaemonJournalEntry
import com.krillsson.sysapi.windows.eventlog.WindowsEventLogRecord


object LogFileAccess

data class LogMessageEdge(
    val cursor: String,
    val node: LogMessage
)

data class LogMessageConnection(
    val edges: List<LogMessageEdge>,
    val pageInfo: PageInfo
)

data class DockerLogMessageEdge(
    val cursor: String,
    val node: DockerLogMessage
)

data class DockerLogMessageConnection(
    val edges: List<DockerLogMessageEdge>,
    val pageInfo: PageInfo
)

data class SystemDaemonJournalEntryEdge(
    val cursor: String,
    val node: SystemDaemonJournalEntry
)

data class SystemDaemonJournalEntryConnection(
    val edges: List<SystemDaemonJournalEntryEdge>,
    val pageInfo: PageInfo
)

data class WindowsEventLogRecordEdge(
    val cursor: String,
    val node: WindowsEventLogRecord
)

data class WindowsEventLogRecordConnection(
    val edges: List<WindowsEventLogRecordEdge>,
    val pageInfo: PageInfo
)

data class PageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String?,
    val endCursor: String?
)
