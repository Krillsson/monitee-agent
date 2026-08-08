package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class PacmanProbe(root: String? = null) : CommandProbe("pacman", root) {

    override val manager = "pacman"

    override val databaseMarkers = listOf(DATABASE_PATH)

    override fun check(): ProbeResult {
        val output = if (root == null && commandExists(CHECKUPDATES_COMMAND)) {
            execute(CHECKUPDATES_COMMAND, setOf(0, 2))
        } else {
            execute(command(), setOf(0, 1))
        }.getOrElse { return failure(it) }
        return ProbeResult.Success(parse(output), null)
    }

    private fun command(): String {
        val root = root ?: return PACMAN_COMMAND
        return "$PACMAN_COMMAND --root $root --dbpath ${underRoot(DATABASE_PATH)}"
    }

    companion object {
        private const val DATABASE_PATH = "var/lib/pacman"
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
