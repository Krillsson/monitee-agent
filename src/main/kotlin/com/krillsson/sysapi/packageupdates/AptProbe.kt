package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage
import java.io.File

class AptProbe(root: String? = null) : CommandProbe("apt-get", root) {

    override val manager = "apt"

    override val databaseMarkers = listOf(DPKG_STATUS)

    override fun check(): ProbeResult {
        val output = execute(command()).getOrElse { return failure(it) }
        val packages = parse(output)
        return ProbeResult.Success(packages, packages.count { it.isSecurity == true })
    }

    private fun command(): String {
        val root = root ?: return "$COMMAND dist-upgrade"
        File(CACHE_DIRECTORY, "archives/partial").mkdirs()
        return "$COMMAND -o Dir=$root -o Dir::Cache=$CACHE_DIRECTORY " +
                "-o Dir::State::status=${underRoot(DPKG_STATUS)} dist-upgrade"
    }

    companion object {
        private const val DPKG_STATUS = "var/lib/dpkg/status"
        private const val CACHE_DIRECTORY = "/tmp/sysapi-apt-cache"
        private const val COMMAND = "apt-get -s -o Debug::NoLocking=true"

        private val INSTALL_LINE = Regex("""^Inst (\S+) (?:\[([^]]*)] )?\(([^ ]+) ([^)]*)\)""")

        fun parse(output: String): List<PendingPackage> = output.lineSequence()
            .mapNotNull { line -> INSTALL_LINE.find(line) }
            .map { match ->
                val (name, currentVersion, newVersion, origin) = match.destructured
                PendingPackage(
                    name = name,
                    currentVersion = currentVersion.ifEmpty { null },
                    newVersion = newVersion,
                    isSecurity = origin.contains("-security", ignoreCase = true)
                )
            }
            .toList()
    }
}
