package com.krillsson.sysapi.launcher

import com.krillsson.sysapi.BuildConfig
import java.io.PrintStream

object LaunchCommand {
    const val PROBE = "--native-probe"
    const val SELF_TEST = "--native-self-test"

    fun run(
        args: Array<String>,
        out: PrintStream,
        inNativeImage: Boolean,
        selfTest: () -> NativeSelfTest
    ): Int? = when (args.firstOrNull()) {
        PROBE -> {
            out.println(probeLine(BuildConfig.APP_VERSION, inNativeImage))
            0
        }

        SELF_TEST -> selfTest().run(out, inNativeImage)
        else -> null
    }

    fun probeLine(version: String, inNativeImage: Boolean): String =
        "sysapi $version ${if (inNativeImage) "native" else "jvm"}"
}
