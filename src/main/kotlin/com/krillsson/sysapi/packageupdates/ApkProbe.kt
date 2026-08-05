package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class ApkProbe(root: String? = null) : CommandProbe("apk", root) {

    override val manager = "apk"

    override val databaseMarkers = listOf("lib/apk/db/installed")

    override fun check(): ProbeResult {
        val output = execute(command()).getOrElse { return failure(it) }
        return ProbeResult.Success(parse(output), null)
    }

    private fun command() = if (root == null) COMMAND else "$COMMAND --root $root"

    companion object {
        private const val COMMAND = "apk list --upgradable"

        private val UPGRADABLE_LINE = Regex("""^(\S+)\s.*\[upgradable from:\s*(\S+)]""")

        fun parse(output: String): List<PendingPackage> = output.lineSequence()
            .mapNotNull { line -> UPGRADABLE_LINE.find(line) }
            .map { match ->
                val (versionedName, versionedCurrentName) = match.destructured
                PendingPackage(
                    name = versionedName.packageNameFromVersionedName(),
                    currentVersion = versionedCurrentName.versionFromVersionedName(),
                    newVersion = versionedName.versionFromVersionedName(),
                    isSecurity = null
                )
            }
            .toList()

        private fun String.versionFromVersionedName() = removePrefix("${packageNameFromVersionedName()}-")
    }
}
