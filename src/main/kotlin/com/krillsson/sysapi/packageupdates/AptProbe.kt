package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class AptProbe : CommandProbe("apt-get") {

    override val manager = "apt"

    override fun check(): ProbeResult {
        val output = execute(COMMAND).getOrElse { return failure(it) }
        val packages = parse(output)
        return ProbeResult.Success(packages, packages.count { it.isSecurity == true })
    }

    companion object {
        private const val COMMAND = "apt-get -s -o Debug::NoLocking=true dist-upgrade"

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
