package com.krillsson.sysapi.logaccess.file

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.filebrowser.FileBrowserException
import com.krillsson.sysapi.filebrowser.FileBrowserSandbox
import com.krillsson.sysapi.filebrowser.FileTypeRegistry
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.name

class LogFileAccessDeniedException(path: String) :
    RuntimeException("$path is not one of the log files this agent exposes")

@Service
class LogFileAccessAuthorizer(
    private val logFilesManager: LogFilesManager,
    private val fileBrowserSandbox: FileBrowserSandbox,
    private val fileBrowserConfiguration: FileBrowserConfiguration,
    private val fileTypeRegistry: FileTypeRegistry
) {

    val logger by logger()

    fun authorize(path: String): File {
        return authorizedFileOrNull(path) ?: run {
            logger.warn("Denied log file access to $path")
            throw LogFileAccessDeniedException(path)
        }
    }

    fun authorizedFileOrNull(path: String): File? {
        return exposedLogFileOrNull(path) ?: browsableFileOrNull(path)
    }

    private fun exposedLogFileOrNull(path: String): File? {
        val requested = requestedRealPath(path) ?: return null
        return logFilesManager.files()
            .map { it.file }
            .firstOrNull { exposedRealPath(it) == requested }
    }

    private fun browsableFileOrNull(path: String): File? {
        val browsed = runCatching { fileBrowserSandbox.resolveExisting(path) }
            .getOrElse { if (it is FileBrowserException) null else throw it }
            ?: return null
        if (!Files.isRegularFile(browsed, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        if (!fileTypeRegistry.looksLikeALogFile(browsed.name)) {
            return null
        }
        if (Files.size(browsed) > fileBrowserConfiguration.maxLogViewBytes) {
            logger.warn("$path is larger than the ${fileBrowserConfiguration.maxLogViewBytes} byte log viewing limit")
            return null
        }
        return browsed.toFile()
    }

    private fun requestedRealPath(path: String): Path? {
        val candidate = runCatching { Path.of(path) }.getOrNull() ?: return null
        if (!candidate.isAbsolute) return null
        return runCatching { candidate.normalize().toRealPath() }.getOrNull()
    }

    private fun exposedRealPath(file: File): Path? =
        runCatching { file.toPath().toAbsolutePath().normalize().toRealPath() }.getOrNull()
}
