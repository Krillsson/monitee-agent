package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.bash.Bash
import com.krillsson.sysapi.cmd.Cmd
import com.krillsson.sysapi.core.domain.system.Platform
import org.springframework.stereotype.Component

@Component
class Ping(val platform: Platform) {

    companion object {
        const val COMMAND = "ping"
        private const val GRACE_MILLIS = 2000L

        private val ROUND_TRIP_TIME = Regex("""[=<]\s*(\d+(?:[.,]\d+)?)\s*ms""")

        fun roundTripMillis(output: String): Double? = ROUND_TRIP_TIME.find(output)
            ?.groupValues
            ?.get(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
    }

    private enum class Flavour { IPUTILS, BSD, WINDOWS }

    private val flavour = when (platform) {
        Platform.WINDOWS, Platform.WINDOWSCE -> Flavour.WINDOWS
        Platform.LINUX, Platform.ANDROID, Platform.GNU -> Flavour.IPUTILS
        Platform.MACOS, Platform.FREEBSD, Platform.OPENBSD, Platform.NETBSD, Platform.KFREEBSD -> Flavour.BSD
        else -> null
    }

    val supported = flavour != null

    // Not Bash.checkIfCommandExists: it asks with `&> /dev/null`, which dash parses as a background
    // job followed by a redirect, so under /bin/sh -> dash it answers 0 for a command that is not there.
    fun installed(): Boolean = when (flavour) {
        null -> false
        Flavour.WINDOWS -> Cmd.checkIfCommandExists(COMMAND).getOrNull() ?: false
        else -> Bash.executeToExitStatus("command -v $COMMAND >/dev/null 2>&1").getOrNull() == 0
    }

    fun command(host: String, timeoutSeconds: Int): String? = when (flavour) {
        Flavour.IPUTILS -> "$COMMAND -n -c 1 -W $timeoutSeconds $host"
        Flavour.BSD -> "$COMMAND -n -c 1 -t $timeoutSeconds $host"
        Flavour.WINDOWS -> "$COMMAND -n 1 -w ${timeoutSeconds * 1000} $host"
        null -> null
    }

    fun run(host: String, timeoutSeconds: Int): Result<String> {
        if (!CheckHost.isValid(host)) {
            return Result.failure(IllegalArgumentException("\"$host\" is not a host name or address"))
        }
        val command = command(host, timeoutSeconds)
            ?: return Result.failure(UnsupportedOperationException("No ping command for $platform"))
        val watchdogMillis = timeoutSeconds * 1000L + GRACE_MILLIS
        return if (flavour == Flavour.WINDOWS) {
            Cmd.executeToText(command, watchdogMillis)
        } else {
            Bash.executeToText(command, watchdogMillis)
        }
    }
}
