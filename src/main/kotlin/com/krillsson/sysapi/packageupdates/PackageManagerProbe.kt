package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.bash.Bash
import com.krillsson.sysapi.core.domain.system.PendingPackage

sealed class ProbeResult {
    data class Success(val packages: List<PendingPackage>, val securityCount: Int?) : ProbeResult()
    data class Failed(val reason: String) : ProbeResult()
}

interface PackageManagerProbe {
    val manager: String
    fun isSupported(): Boolean
    fun check(): ProbeResult
}

abstract class CommandProbe(private val executable: String) : PackageManagerProbe {

    companion object {
        const val TIMEOUT_MILLIS = 60L * 1000
    }

    override fun isSupported(): Boolean =
        Bash.executeToExitStatus("command -v $executable >/dev/null 2>&1", TIMEOUT_MILLIS).getOrNull() == 0

    protected fun execute(command: String, acceptedExitCodes: Set<Int> = setOf(0)): Result<String> {
        val output = Bash.executeToTextAndExitStatus(command, TIMEOUT_MILLIS)
            .getOrElse { return Result.failure(it) }
        return if (acceptedExitCodes.contains(output.exitCode)) {
            Result.success(output.text)
        } else {
            Result.failure(IllegalStateException("`$command` exited with ${output.exitCode}"))
        }
    }

    protected fun failure(throwable: Throwable) = ProbeResult.Failed(throwable.message ?: throwable.toString())
}

fun String.packageNameFromVersionedName(): String = substringBeforeLast('-').substringBeforeLast('-')
