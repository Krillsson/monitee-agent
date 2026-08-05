package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class ZypperProbe(root: String? = null) : CommandProbe("zypper", root) {

    override val manager = "zypper"

    override val databaseMarkers = DnfProbe.RPM_DATABASES

    override fun check(): ProbeResult {
        val output = execute(command(UPDATES_ARGUMENTS), ACCEPTED_EXIT_CODES).getOrElse { return failure(it) }
        val packages = parse(output)
        val patches = execute(command(PATCHES_ARGUMENTS), ACCEPTED_EXIT_CODES).getOrNull()
            ?: return ProbeResult.Success(packages, null)
        return ProbeResult.Success(packages, countSecurityPatches(patches))
    }

    private fun command(arguments: String): String {
        val root = root ?: return "$COMMAND $arguments"
        return "$COMMAND --root $root $arguments"
    }

    companion object {
        private const val COMMAND = "zypper -q --non-interactive"
        private const val UPDATES_ARGUMENTS = "list-updates"
        private const val PATCHES_ARGUMENTS = "list-patches --category security"

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
