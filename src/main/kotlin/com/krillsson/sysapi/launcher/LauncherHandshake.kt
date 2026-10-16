package com.krillsson.sysapi.launcher

import com.krillsson.sysapi.BuildConfig
import java.io.File
import kotlin.system.exitProcess

class LauncherHandshake(
    private val environment: Map<String, String>,
    private val inNativeImage: Boolean,
    private val version: String
) {

    fun onReady() {
        environment[READY_FILE]?.let { write(it, if (inNativeImage) "native" else "jvm") }
        environment[NATIVE_DISABLED_FILE]?.let { write(it, nativeDisabledMarker()) }
    }

    fun launcherPid(ownPid: Long): Long? = environment[LAUNCHER_PID]?.toLongOrNull()?.takeIf { it != ownPid }

    fun exitWhenLauncherExits() {
        val pid = launcherPid(ProcessHandle.current().pid()) ?: return
        ProcessHandle.of(pid).ifPresentOrElse(
            { launcher -> launcher.onExit().thenRun { exitProcess(0) } },
            { exitProcess(0) }
        )
    }

    fun nativeDisabledMarker(): String =
        "$version\nunclassified\nThe native binary exited before startup and the JVM started, so the failure was specific to the native image.\n"

    private fun write(path: String, content: String) {
        File(path).apply { parentFile?.mkdirs() }.writeText(content)
    }

    companion object {
        const val READY_FILE = "SYSAPI_READY_FILE"
        const val NATIVE_DISABLED_FILE = "SYSAPI_NATIVE_DISABLED_FILE"
        const val LAUNCHER_PID = "SYSAPI_LAUNCHER_PID"

        fun fromEnvironment(inNativeImage: Boolean) =
            LauncherHandshake(System.getenv(), inNativeImage, BuildConfig.APP_VERSION)
    }
}
