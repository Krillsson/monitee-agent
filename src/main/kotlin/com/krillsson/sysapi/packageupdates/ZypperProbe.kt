package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class ZypperProbe : CommandProbe("zypper") {

    override val manager = "zypper"

    override fun check(): ProbeResult {
        val output = execute(UPDATES_COMMAND, ACCEPTED_EXIT_CODES).getOrElse { return failure(it) }
        val packages = parse(output)
        val patches = execute(PATCHES_COMMAND, ACCEPTED_EXIT_CODES).getOrNull()
            ?: return ProbeResult.Success(packages, null)
        return ProbeResult.Success(packages, countSecurityPatches(patches))
    }

    companion object {
        private const val UPDATES_COMMAND = "zypper -q --non-interactive list-updates"
        private const val PATCHES_COMMAND = "zypper -q --non-interactive list-patches --category security"

        private val ACCEPTED_EXIT_CODES = setOf(0, 100, 101)

        fun parse(output: String): List<PendingPackage> = output.lineSequence()
            .map { line -> line.split('|').map(String::trim) }
            .filter { columns -> columns.size >= 6 && columns.first() == "v" }
            .map { columns ->
                PendingPackage(
                    name = columns[2],
                    currentVersion = columns[3],
                    newVersion = columns[4],
                    isSecurity = null
                )
            }
            .toList()

        fun countSecurityPatches(output: String): Int = output.lineSequence()
            .map { line -> line.split('|').map(String::trim) }
            .count { columns ->
                columns.size >= 6 &&
                        columns.first().isNotEmpty() &&
                        columns.first() != "Repository" &&
                        columns.first().any { it.isLetterOrDigit() }
            }
    }
}
