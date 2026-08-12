package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.util.FileSystem
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

open class FileBrowserException(message: String) : RuntimeException(message)

class FileAlreadyThereException(message: String) : FileBrowserException(message)

@Service
class FileBrowserSandbox(configuration: FileBrowserConfiguration) {

    companion object {
        private const val MAX_NAME_LENGTH = 255
    }

    val logger by logger()

    val roots: List<Path> = if (configuration.enabled) resolveRoots(configuration.roots) else emptyList()

    val enabled: Boolean = configuration.enabled && roots.isNotEmpty()

    val access: FileBrowserAccess = configuration.access

    val writable: Boolean = access == FileBrowserAccess.READ_WRITE

    init {
        if (configuration.enabled) {
            if (roots.isEmpty()) {
                logger.warn("The file browser is enabled but none of its configured roots could be used")
            } else {
                logger.info("File browser serving ${roots.joinToString { it.toString() }} in $access mode")
                warnAboutReachableAgentDirectories()
            }
        }
    }

    fun requireEnabled() {
        if (!enabled) {
            throw FileBrowserException("The file browser is disabled")
        }
    }

    fun requireWritable() {
        requireEnabled()
        if (!writable) {
            throw FileBrowserException("The file browser is in $access mode")
        }
    }

    fun resolveExisting(path: String): Path {
        requireEnabled()
        val real = realPathOf(parse(path))
        if (!Files.exists(real, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$path does not exist")
        }
        if (Files.isSymbolicLink(real)) {
            throw FileBrowserException("$path is a symbolic link, which the file browser does not follow")
        }
        return real
    }

    fun resolveForCreate(path: String): Path {
        requireEnabled()
        val real = realPathOf(parse(path))
        if (Files.isSymbolicLink(real)) {
            throw FileBrowserException("$path is a symbolic link, which the file browser does not follow")
        }
        return real
    }

    fun resolveInDirectory(directory: String, name: String): Path {
        val parent = resolveExisting(directory)
        if (!Files.isDirectory(parent)) {
            throw FileBrowserException("$directory is not a directory")
        }
        return resolveForCreate(parent.resolve(validName(name)).toString())
    }

    fun isRoot(path: Path) = roots.any { it == path }

    fun contains(path: Path) = roots.any { path.startsWith(it) }

    fun validName(name: String): String {
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) {
            throw FileBrowserException("A file name has to be between 1 and $MAX_NAME_LENGTH characters")
        }
        if (name == "." || name == "..") {
            throw FileBrowserException("$name is not a file name")
        }
        if (name.any { it == '/' || it == '\\' || it.isISOControl() }) {
            throw FileBrowserException("A file name cannot contain separators or control characters")
        }
        return name
    }

    private fun parse(path: String): Path {
        if (path.isEmpty()) {
            throw FileBrowserException("An empty path cannot be resolved")
        }
        if (path.any { it.isISOControl() }) {
            throw FileBrowserException("A path cannot contain control characters")
        }
        val candidate = runCatching { Path.of(path) }.getOrNull()
            ?: throw FileBrowserException("$path is not a path on this system")
        if (!candidate.isAbsolute) {
            throw FileBrowserException("$path is not an absolute path")
        }
        val normalized = candidate.normalize()
        if (!contains(normalized)) {
            throw FileBrowserException("$path is outside of the directories this agent exposes")
        }
        return normalized
    }

    /**
     * The parent is resolved with `toRealPath`, which follows every symbolic link on the way, and
     * containment is checked again on the result, so a link pointing out of a root fails even
     * though the path it was written as normalizes to something inside one. The last element is
     * deliberately left unresolved, so that a symlink can still be told apart from what it points at.
     */
    private fun realPathOf(normalized: Path): Path {
        val parent = normalized.parent
        val real = if (parent == null) {
            realPath(normalized) ?: throw FileBrowserException("$normalized does not exist")
        } else {
            val realParent = realPath(parent) ?: throw FileBrowserException("$parent does not exist")
            realParent.resolve(normalized.fileName)
        }
        if (!contains(real)) {
            throw FileBrowserException("$normalized is outside of the directories this agent exposes")
        }
        return real
    }

    private fun realPath(path: Path): Path? = runCatching { path.toRealPath() }.getOrNull()

    private fun warnAboutReachableAgentDirectories() {
        listOf(FileSystem.config, FileSystem.data)
            .mapNotNull { realPath(it.toPath().toAbsolutePath()) }
            .filter { contains(it) }
            .forEach {
                logger.warn(
                    "The file browser can reach the agent's own $it. " +
                            "In READ_WRITE mode that means its configuration and database can be changed through the API"
                )
            }
    }

    private fun resolveRoots(configured: List<String>): List<Path> {
        val resolved = configured.mapNotNull { path ->
            val candidate = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()
            val real = candidate?.let { realPath(it) }
            when {
                real == null -> {
                    logger.error("File browser root $path does not exist")
                    null
                }

                !Files.isDirectory(real) -> {
                    logger.error("File browser root $path is not a directory")
                    null
                }

                else -> real
            }
        }
        return resolved.filter { root -> resolved.none { it != root && root.startsWith(it) } }.distinct()
    }
}
