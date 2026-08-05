package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.cmd.Cmd
import com.krillsson.sysapi.core.domain.system.PendingPackage
import java.util.Base64

class WindowsUpdateProbe : PackageManagerProbe {

    override val manager = "Windows Update"

    override fun isSupported(): Boolean = Cmd.checkIfCommandExists("powershell").getOrNull() ?: false

    override fun check(): ProbeResult {
        val output = Cmd.executeToText(COMMAND, TIMEOUT_MILLIS)
            .getOrElse { return ProbeResult.Failed(it.message ?: it.toString()) }
        if (output.contains(FAILURE_MARKER)) {
            return ProbeResult.Failed(output.substringAfter(FAILURE_MARKER).trim().lines().first())
        }
        val packages = parse(output)
        return ProbeResult.Success(packages, packages.count { it.isSecurity == true })
    }

    companion object {
        private const val TIMEOUT_MILLIS = 120L * 1000
        private const val FAILURE_MARKER = "SYSAPI-SEARCH-FAILED"

        private val SCRIPT = listOf(
            "\$ErrorActionPreference = 'Stop'",
            "try {",
            "\$searcher = (New-Object -ComObject Microsoft.Update.Session).CreateUpdateSearcher()",
            "foreach (\$update in \$searcher.Search('IsInstalled=0 and IsHidden=0').Updates) {",
            "\$security = \$false",
            "foreach (\$category in \$update.Categories) { if (\$category.Name -eq 'Security Updates') { \$security = \$true } }",
            "Write-Output (\$update.Title + '|' + \$security)",
            "}",
            "} catch { Write-Output ('$FAILURE_MARKER ' + \$_.Exception.Message) }"
        ).joinToString("; ")

        private val COMMAND = "powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand " +
                Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE))

        fun parse(output: String): List<PendingPackage> = output.lineSequence()
            .map(String::trim)
            .filter { it.contains('|') }
            .map { line ->
                PendingPackage(
                    name = line.substringBeforeLast('|'),
                    currentVersion = null,
                    newVersion = null,
                    isSecurity = line.substringAfterLast('|').equals("true", ignoreCase = true)
                )
            }
            .toList()
    }
}
