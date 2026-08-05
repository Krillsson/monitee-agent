package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class ApkProbe : CommandProbe("apk") {

    override val manager = "apk"

    override fun check(): ProbeResult {
        val output = execute(COMMAND).getOrElse { return failure(it) }
        return ProbeResult.Success(parse(output), null)
    }

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
