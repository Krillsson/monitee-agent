package com.krillsson.sysapi.bash

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.MappingIterator
import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.JournalLogsConfiguration
import com.krillsson.sysapi.systemd.SystemDaemonJournalEntry
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.toOffsetDateTime
import reactor.core.publisher.Flux
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class JournalCtl(
    private val mapper: ObjectMapper,
    private val journalLogsConfiguration: JournalLogsConfiguration
) {
    companion object {
        private val JOURNALD_TIME_FORMAT_PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun entriesCommand(serviceUnitName: String) = listOf("journalctl", "--output=json", "UNIT=$serviceUnitName")

        fun tailCommand(serviceUnitName: String) = entriesCommand(serviceUnitName) + listOf("-n", "0", "--follow")

        fun linesCommand(
            serviceUnitName: String,
            limit: Int?,
            since: Instant?,
            until: Instant?
        ): List<String> = entriesCommand(serviceUnitName) + when {
            limit != null && limit >= 0 -> listOf("-n", limit.toString())
            else -> timeFilter("--since", since) + timeFilter("--until", until)
        }

        private fun timeFilter(option: String, instant: Instant?) = when (instant) {
            null -> emptyList()
            else -> listOf(option, JOURNALD_TIME_FORMAT_PATTERN.format(instant.toOffsetDateTime()))
        }
    }

    private val logger by logger()

    class JournalForServiceOutput : ArrayList<JournalForServiceOutput.Line>() {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Line(
            @JsonProperty("MESSAGE")
            val message: String,
            @JsonProperty("__REALTIME_TIMESTAMP")
            val timestamp: Long
        )
    }

    fun supportedBySystem(): Boolean {
        return Bash.checkIfCommandExists("journalctl").getOrNull() ?: false
    }

    fun tailLines(
        serviceUnitName: String
    ): Flux<SystemDaemonJournalEntry> {
        return if (journalLogsConfiguration.enabled && CommandArgument.isUnitName(serviceUnitName)) {
            Bash.executeToTextContinuously(tailCommand(serviceUnitName))
                .map { it.convertJsonStringToEntries() }
                .flatMapIterable { it }
        } else {
            Flux.empty<SystemDaemonJournalEntry>()
        }
    }

    fun lines(
        serviceUnitName: String,
        limit: Int? = null,
        since: Instant? = null,
        until: Instant? = null
    ): List<SystemDaemonJournalEntry> {
        return if (journalLogsConfiguration.enabled && CommandArgument.isUnitName(serviceUnitName)) {
            val result = Bash.executeToText(linesCommand(serviceUnitName, limit, since, until))
            result.getOrNull()?.convertJsonStringToEntries().orEmpty()
        } else {
            emptyList()
        }
    }

    fun firstLine(
        serviceUnitName: String
    ): SystemDaemonJournalEntry? {
        return if (journalLogsConfiguration.enabled && CommandArgument.isUnitName(serviceUnitName)) {
            val result = Bash.executeToText(entriesCommand(serviceUnitName))
            result.getOrNull()?.convertJsonStringToEntries().orEmpty().firstOrNull()
        } else {
            null
        }
    }

    private fun String.convertJsonStringToEntries(): List<SystemDaemonJournalEntry> {
        return try {
            val messages = mutableListOf<SystemDaemonJournalEntry>()
            val iterator: MappingIterator<JournalForServiceOutput.Line> =
                mapper.readerFor(JournalForServiceOutput.Line::class.java).readValues(this)
            val lines = iterator.readAll()
            lines.forEach {
                val instant = Instant.ofEpochSecond(TimeUnit.MICROSECONDS.toSeconds(it.timestamp))
                messages += SystemDaemonJournalEntry(instant, it.message)
            }
            return messages
        } catch (e: Exception) {
            logger.error("Unable to parse json", e)
            emptyList()
        }
    }
}