package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import kotlin.io.path.name

@Service
class TrashService(
    private val configuration: FileBrowserConfiguration,
    private val sandbox: FileBrowserSandbox,
    private val manager: FileBrowserManager
) {

    companion object {
        private const val FILES = "files"
        private const val INFO = "info"
        private const val INFO_SUFFIX = ".trashinfo"
        private const val ORIGINAL_PATH = "Path="
        private const val DELETED_AT = "DeletionDate="
        private const val NAME = "Name="
    }

    private data class TrashInfo(val originalPath: String, val deletedAt: Instant?, val name: String?)

    val logger by logger()

    val enabled get() = sandbox.enabled && configuration.trash

    fun trash(path: String): TrashEntry {
        requireEnabled()
        sandbox.requireWritable()
        val target = sandbox.resolveExisting(path)
        if (sandbox.isRoot(target)) {
            throw FileBrowserException("$path is one of the configured roots and cannot be deleted")
        }
        if (sandbox.isInTrash(target)) {
            throw FileBrowserException("$path is already in the trash")
        }
        val root = sandbox.rootOf(target)
        val id = UUID.randomUUID().toString()
        val files = Files.createDirectories(root.resolve(FileBrowserSandbox.TRASH_DIRECTORY).resolve(FILES))
        val info = Files.createDirectories(root.resolve(FileBrowserSandbox.TRASH_DIRECTORY).resolve(INFO))
        val deletedAt = Instant.now()
        Files.write(
            info.resolve(id + INFO_SUFFIX),
            listOf("$ORIGINAL_PATH$target", "$DELETED_AT$deletedAt", "$NAME${target.name}"),
            StandardCharsets.UTF_8
        )
        val stored = files.resolve(id)
        try {
            manager.moveInto(target, stored, overwrite = false)
        } catch (ex: Exception) {
            runCatching { Files.deleteIfExists(info.resolve(id + INFO_SUFFIX)) }
            throw ex
        }
        logger.info("Moved $target to the trash as $id")
        return entryFor(root, id, stored)
    }

    fun list(): List<TrashEntry> {
        requireEnabled()
        return sandbox.roots.flatMap { root ->
            val files = root.resolve(FileBrowserSandbox.TRASH_DIRECTORY).resolve(FILES)
            if (!Files.isDirectory(files)) {
                emptyList()
            } else {
                Files.newDirectoryStream(files).use { stream ->
                    stream.map { stored -> entryFor(root, stored.name, stored) }
                }
            }
        }.sortedByDescending { it.deletedAt ?: Instant.EPOCH }
    }

    fun restore(id: String, overwrite: Boolean): FileEntry {
        requireEnabled()
        sandbox.requireWritable()
        val root = rootHolding(id)
        val stored = storedFile(root, id)
        val original = readInfo(root, id)?.originalPath
            ?: throw FileBrowserException("Where $id came from is not recorded, so it cannot be restored")
        val destination = sandbox.resolveForCreate(original)
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !overwrite) {
            throw FileAlreadyThereException("$original is there again. Send overwrite to replace it")
        }
        Files.createDirectories(requireNotNull(destination.parent))
        manager.moveInto(stored, destination, overwrite)
        runCatching { Files.deleteIfExists(infoFile(root, id)) }
        logger.info("Restored $id to $destination")
        return manager.entryOf(destination)
    }

    fun requireEmptyable(id: String?) {
        requireEnabled()
        sandbox.requireWritable()
        if (id != null) {
            rootHolding(id)
        }
    }

    fun empty(id: String?, sink: FileOperationSink = NoFileOperationSink): Int {
        requireEmptyable(id)
        val emptied = if (id == null) {
            sandbox.roots.flatMap { root -> idsIn(root).map { root to it } }
        } else {
            listOf(rootHolding(id) to id)
        }
        emptied.forEach { (root, each) ->
            sink.requireNotCancelled()
            manager.runDelete(storedFile(root, each), sink)
            runCatching { Files.deleteIfExists(infoFile(root, each)) }
        }
        logger.info("Emptied ${emptied.size} entries from the trash")
        return emptied.size
    }

    private fun requireEnabled() {
        sandbox.requireEnabled()
        if (!configuration.trash) {
            throw FileBrowserException("The trash is switched off")
        }
    }

    private fun idsIn(root: Path): List<String> {
        val files = root.resolve(FileBrowserSandbox.TRASH_DIRECTORY).resolve(FILES)
        if (!Files.isDirectory(files)) {
            return emptyList()
        }
        return Files.newDirectoryStream(files).use { stream -> stream.map { it.name } }
    }

    private fun rootHolding(id: String): Path {
        sandbox.validName(id)
        return sandbox.roots.firstOrNull { Files.exists(storedFile(it, id), LinkOption.NOFOLLOW_LINKS) }
            ?: throw FileBrowserException("$id is not in the trash")
    }

    private fun storedFile(root: Path, id: String): Path =
        root.resolve(FileBrowserSandbox.TRASH_DIRECTORY).resolve(FILES).resolve(sandbox.validName(id))

    private fun infoFile(root: Path, id: String): Path =
        root.resolve(FileBrowserSandbox.TRASH_DIRECTORY).resolve(INFO).resolve(sandbox.validName(id) + INFO_SUFFIX)

    private fun entryFor(root: Path, id: String, stored: Path): TrashEntry {
        val info = readInfo(root, id)
        val entry = manager.entryOf(stored)
        val name = info?.name ?: id
        return TrashEntry(
            id = id,
            entry = entry.copy(name = name),
            originalPath = info?.originalPath ?: id,
            deletedAt = info?.deletedAt,
            root = root.toString()
        )
    }

    private fun readInfo(root: Path, id: String): TrashInfo? {
        val file = infoFile(root, id)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        val lines = runCatching { Files.readAllLines(file, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val path = lines.firstOrNull { it.startsWith(ORIGINAL_PATH) }?.removePrefix(ORIGINAL_PATH) ?: return null
        val deletedAt = lines.firstOrNull { it.startsWith(DELETED_AT) }
            ?.removePrefix(DELETED_AT)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val name = lines.firstOrNull { it.startsWith(NAME) }?.removePrefix(NAME)
        return TrashInfo(path, deletedAt, name)
    }
}
