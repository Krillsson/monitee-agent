package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class DnfProbe(private val executable: String, root: String? = null) : CommandProbe(executable, root) {

    override val manager = executable

    override val databaseMarkers = RPM_DATABASES

    override fun check(): ProbeResult {
        val output = execute("${command()} check-update", UPDATES_AVAILABLE).getOrElse { return failure(it) }
        val packages = parse(output)
        val securityNames = securityPackageNames()
            ?: return ProbeResult.Success(packages, null)
        val withSecurity = packages.map { it.copy(isSecurity = securityNames.contains(it.name)) }
        return ProbeResult.Success(withSecurity, withSecurity.count { it.isSecurity == true })
    }

    private fun command() = if (root == null) "$executable -q" else "$executable -q --installroot=$root"

    private fun securityPackageNames(): Set<String>? {
        val output = execute("${command()} updateinfo list --security").getOrNull()
            ?: execute("${command()} updateinfo list security").getOrNull()
            ?: return null
        return parseSecurityNames(output)
    }

    companion object {
        val RPM_DATABASES = listOf("var/lib/rpm", "usr/lib/sysimage/rpm")

        private val UPDATES_AVAILABLE = setOf(0, 100)

        fun parse(output: String): List<PendingPackage> {
            val lines = output.lineSequence()
                .takeWhile { !it.startsWith("Obsoleting") }
                .map { it.split(' ', '\t').filter(String::isNotBlank) }
                .toList()

            val packages = mutableListOf<PendingPackage>()
            var wrappedName: String? = null
            lines.forEach { tokens ->
                val name = wrappedName
                when {
                    name != null && tokens.size == 2 && tokens.first().isVersion() -> {
                        packages.add(pendingPackage(name, tokens.first()))
                        wrappedName = null
                    }

                    tokens.size == 3 && tokens.first().contains('.') && tokens[1].isVersion() -> {
                        packages.add(pendingPackage(tokens.first(), tokens[1]))
                        wrappedName = null
                    }

                    tokens.size == 1 && tokens.first().contains('.') -> wrappedName = tokens.first()
                    else -> wrappedName = null
                }
            }
            return packages
        }

        fun parseSecurityNames(output: String): Set<String> = output.lineSequence()
            .map { it.split(' ', '\t').filter(String::isNotBlank) }
            .filter { it.size > 1 }
            .map { it.last().substringBeforeLast('.').packageNameFromVersionedName() }
            .filter { it.isNotEmpty() }
            .toSet()

        private fun pendingPackage(nameWithArchitecture: String, version: String) = PendingPackage(
            name = nameWithArchitecture.substringBeforeLast('.'),
            currentVersion = null,
            newVersion = version,
            isSecurity = null
        )

        private fun String.isVersion() = firstOrNull()?.isDigit() == true
    }
}
