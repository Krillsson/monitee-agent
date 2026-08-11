package com.krillsson.sysapi.logaccess.file

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path

class LogFileAccessDeniedException(path: String) :
    RuntimeException("$path is not one of the log files this agent exposes")

@Service
class LogFileAccessAuthorizer(private val logFilesManager: LogFilesManager) {

    val logger by logger()

    fun authorize(path: String): File {
        return authorizedFileOrNull(path) ?: run {
            logger.warn("Denied log file access to $path")
            throw LogFileAccessDeniedException(path)
        }
    }

    fun authorizedFileOrNull(path: String): File? {
        val requested = requestedRealPath(path) ?: return null
        return logFilesManager.files()
            .map { it.file }
            .firstOrNull { exposedRealPath(it) == requested }
    }

    private fun requestedRealPath(path: String): Path? {
        val candidate = runCatching { Path.of(path) }.getOrNull() ?: return null
        if (!candidate.isAbsolute) return null
        return runCatching { candidate.normalize().toRealPath() }.getOrNull()
    }

    private fun exposedRealPath(file: File): Path? =
        runCatching { file.toPath().toAbsolutePath().normalize().toRealPath() }.getOrNull()
}
