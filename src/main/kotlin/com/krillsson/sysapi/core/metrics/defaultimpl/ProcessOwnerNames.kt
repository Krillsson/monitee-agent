package com.krillsson.sysapi.core.metrics.defaultimpl

import oshi.software.os.OSProcess
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ProcessOwnerNames(
    private val maxAge: Duration,
    private val clock: Clock
) {

    private data class Resolved(val name: String, val at: Instant)

    private val users = ConcurrentHashMap<String, Resolved>()
    private val groups = ConcurrentHashMap<String, Resolved>()

    fun refresh(processes: List<OSProcess>) {
        refresh(users, processes.associateBy { it.userID }) { it.user }
        refresh(groups, processes.associateBy { it.groupID }) { it.group }
    }

    fun userOf(process: OSProcess): String =
        users.computeIfAbsent(process.userID) { Resolved(process.user, clock.instant()) }.name

    fun groupOf(process: OSProcess): String =
        groups.computeIfAbsent(process.groupID) { Resolved(process.group, clock.instant()) }.name

    private fun refresh(
        names: MutableMap<String, Resolved>,
        processById: Map<String, OSProcess>,
        resolve: (OSProcess) -> String
    ) {
        val now = clock.instant()
        names.keys.retainAll(processById.keys)
        processById
            .filterKeys { id -> isStale(names[id], now) }
            .forEach { (id, process) -> names[id] = Resolved(resolve(process), now) }
    }

    private fun isStale(resolved: Resolved?, now: Instant): Boolean =
        resolved == null || !resolved.at.plus(maxAge).isAfter(now)
}
