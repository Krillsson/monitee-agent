package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.core.domain.system.PendingPackage

class DnfProbe(private val executable: String) : CommandProbe(executable) {

    override val manager = executable

    override fun check(): ProbeResult {
        val output = execute("$executable -q check-update", UPDATES_AVAILABLE).getOrElse { return failure(it) }
        val packages = parse(output)
        val securityNames = securityPackageNames()
            ?: return ProbeResult.Success(packages, null)
        val withSecurity = packages.map { it.copy(isSecurity = securityNames.contains(it.name)) }
        return ProbeResult.Success(withSecurity, withSecurity.count { it.isSecurity == true })
    }

    private fun securityPackageNames(): Set<String>? {
        val output = execute("$executable -q updateinfo list --security").getOrNull()
            ?: execute("$executable -q updateinfo list security").getOrNull()
            ?: return null
        return parseSecurityNames(output)
    }

    companion object {
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
