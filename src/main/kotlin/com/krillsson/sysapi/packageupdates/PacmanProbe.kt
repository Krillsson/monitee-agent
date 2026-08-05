package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.bash.Bash
import com.krillsson.sysapi.core.domain.system.PendingPackage

class PacmanProbe : CommandProbe("pacman") {

    override val manager = "pacman"

    override fun check(): ProbeResult {
        val output = if (hasCheckupdates()) {
            execute(CHECKUPDATES_COMMAND, setOf(0, 2))
        } else {
            execute(PACMAN_COMMAND, setOf(0, 1))
        }.getOrElse { return failure(it) }
        return ProbeResult.Success(parse(output), null)
    }

    private fun hasCheckupdates() =
        Bash.executeToExitStatus("command -v checkupdates >/dev/null 2>&1", TIMEOUT_MILLIS).getOrNull() == 0

    companion object {
        private const val CHECKUPDATES_COMMAND = "checkupdates"
        private const val PACMAN_COMMAND = "pacman -Qu"

        private val UPGRADE_LINE = Regex("""^(\S+) (\S+) -> (\S+)$""")

        fun parse(output: String): List<PendingPackage> = output.lineSequence()
            .map(String::trim)
            .mapNotNull { line -> UPGRADE_LINE.find(line) }
            .map { match ->
                val (name, currentVersion, newVersion) = match.destructured
                PendingPackage(
                    name = name,
                    currentVersion = currentVersion,
                    newVersion = newVersion,
                    isSecurity = null
                )
            }
            .toList()
    }
}
