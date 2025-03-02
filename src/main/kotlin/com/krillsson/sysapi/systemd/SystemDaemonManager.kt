package com.krillsson.sysapi.systemd

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.bash.JournalCtl
import com.krillsson.sysapi.bash.SystemCtl
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.graphql.domain.PageInfo
import com.krillsson.sysapi.graphql.domain.SystemDaemonAccessAvailable
import com.krillsson.sysapi.graphql.domain.SystemDaemonJournalEntryConnection
import com.krillsson.sysapi.graphql.domain.SystemDaemonJournalEntryEdge
import com.krillsson.sysapi.util.decodeAsInstantCursor
import com.krillsson.sysapi.util.encodeAsCursor
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Instant

@Service
class SystemDaemonManager(
    mapper: ObjectMapper,
    private val config: YAMLConfigFile
) : SystemDaemonAccessAvailable {

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val error: RuntimeException) : Status()
    }

    val logger by logger()

    private val journalCtl = JournalCtl(mapper, config.linux.journalLogs)
    private val systemCtl = SystemCtl(mapper)

    private val supportedBySystem = journalCtl.supportedBySystem() && systemCtl.supportedBySystem()

    fun status(): Status {
        return when {
            !config.linux.systemDaemonServiceManagement.enabled -> Status.Disabled
            !supportedBySystem -> Status.Unavailable(RuntimeException("systemctl or journalctl command was not found"))
            else -> Status.Available
        }
    }

    override fun openJournal(name: String, limit: Int): List<SystemDaemonJournalEntry> {
        return when {
            !config.linux.journalLogs.enabled -> emptyList()
            !supportedBySystem -> emptyList()
            else -> journalCtl.lines(name, limit)
        }
    }

    override fun services(): List<SystemCtl.ListServicesOutput.Item> {
        return when {
            !config.linux.journalLogs.enabled -> emptyList()
            !supportedBySystem -> emptyList()
            else -> systemCtl.services()
        }
    }

    override fun serviceDetails(name: String): SystemCtl.ServiceDetailsOutput? {
        return when {
            !config.linux.journalLogs.enabled -> null
            !supportedBySystem -> null
            else -> systemCtl.serviceDetails(name)
        }
    }

    fun performCommandWithService(serviceName: String, command: SystemDaemonCommand): CommandResult {
        return when {
            !config.linux.journalLogs.enabled -> CommandResult.Disabled
            !supportedBySystem -> CommandResult.Unavailable
            else -> systemCtl.performCommandWithService(serviceName, command)
        }
    }

    override fun openJournalConnection(
        name: String,
        after: String?,
        before: String?,
        first: Int?,
        last: Int?,
        reverse: Boolean?
    ): SystemDaemonJournalEntryConnection {
        val latestTimestamp = journalCtl.lines(name, limit = 1).firstOrNull()?.timestamp
        val firstTimestamp = journalCtl.firstLine(name)?.timestamp

        val (fromTimestamp, toTimestamp) = if (reverse == true) {
            before?.decodeAsInstantCursor() to after?.decodeAsInstantCursor()
        } else {
            after?.decodeAsInstantCursor() to before?.decodeAsInstantCursor()
        }
        val pageSize = first ?: last ?: 10

        val filteredLogs = when {
            reverse == true && fromTimestamp == null && toTimestamp == null -> {
                journalCtl.lines(name, limit = pageSize)
                    .sortedByDescending { it.timestamp }
            }

            else -> {
                journalCtl.lines(
                    serviceUnitName = name,
                    since = fromTimestamp,
                    until = toTimestamp
                )
            }
        }

        val sortedLogs = if (reverse == true) {
            filteredLogs.sortedByDescending { it.timestamp }
        } else {
            filteredLogs
        }

        val paginatedLogs = when {
            first != null -> sortedLogs.take(first)
            last != null -> sortedLogs.takeLast(last)
            else -> sortedLogs.take(pageSize)
        }

        val (hasNext, hasPrevious) = when {
            reverse == true -> {
                paginatedLogs.lastOrNull()?.timestamp?.let { lastInSet ->
                    firstTimestamp?.isBefore(lastInSet)
                } to paginatedLogs.firstOrNull()?.timestamp?.let { firstInSet ->
                    latestTimestamp?.isAfter(firstInSet)
                }
            }

            else -> {
                paginatedLogs.lastOrNull()?.timestamp?.let { lastInSet ->
                    latestTimestamp?.isAfter(lastInSet)
                } to paginatedLogs.firstOrNull()?.timestamp?.let { firstInSet ->
                    firstTimestamp?.isBefore(firstInSet)
                }
            }
        }

        val edges = paginatedLogs.map {
            SystemDaemonJournalEntryEdge(
                cursor = it.timestamp.encodeAsCursor(),
                node = it
            )
        }

        val pageInfo = PageInfo(
            hasNextPage = hasNext ?: false,
            hasPreviousPage = hasPrevious ?: false,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor
        )
        logger.debug("Service: $name, after: $after, before: $before, first: $first, last: $last, reverse: $reverse")
        logger.debug("Returning info: $pageInfo and ${edges.size} edges")
        return SystemDaemonJournalEntryConnection(
            edges = edges,
            pageInfo = pageInfo
        )
    }

    override fun openAndTailJournal(name: String, startPosition: String?, reverse: Boolean?): Flux<SystemDaemonJournalEntry> {
        val historicalLines = if (startPosition != null) {
            val timestamp = startPosition.decodeAsInstantCursor()
            journalCtl.lines(
                name,
                since = timestamp,
                until = Instant.now()
            )
                .let { list -> if (reverse == true) list.sortedByDescending { it.timestamp } else list }
                .filter { it.timestamp.isAfter(timestamp) }
        } else {
            emptyList()
        }

        return journalCtl
            .tailLines(name)
            .startWith(Flux.fromIterable(historicalLines))
    }

}
