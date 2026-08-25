package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import com.krillsson.sysapi.config.FileBrowserConfiguration
import org.junit.jupiter.api.Assumptions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.Instant

class FileBrowser(val configuration: FileBrowserConfiguration, cache: Path) {
    val fileTypeRegistry = FileTypeRegistry()
    val sandbox = FileBrowserSandbox(configuration)
    val entryFactory = FileEntryFactory(configuration, fileTypeRegistry)
    val manager = FileBrowserManager(configuration, sandbox, entryFactory)
    val treeWalker = FileTreeWalker(configuration, sandbox, entryFactory)
    val archives = ArchiveService(configuration, sandbox, manager, fileTypeRegistry)
    val archiveBrowser = ArchiveBrowser(configuration, sandbox, entryFactory, fileTypeRegistry)
    val trash = TrashService(configuration, sandbox, manager)
    val registry = FileOperationRegistry(configuration)
    val operations = FileOperationService(sandbox, manager, archives, trash, registry, treeWalker)

    fun await(operation: FileOperation): FileOperation =
        operations.events(operation.id).blockLast(Duration.ofSeconds(20))
            ?: error("The ${operation.type} operation never reported anything")
    val thumbnails = ThumbnailService(configuration, sandbox, fileTypeRegistry, cache)
}

fun fileBrowser(
    root: Path,
    cache: Path = root.resolveSibling("thumbnail-cache"),
    access: FileBrowserAccess = FileBrowserAccess.READ_WRITE,
    maxEditableBytes: Long = 1024,
    maxUploadBytes: Long = 0,
    trash: Boolean = true,
    searchTimeoutSeconds: Long = 20,
    thumbnails: Boolean = true,
    maxThumbnailSourceBytes: Long = 67_108_864,
    maxThumbnailCacheBytes: Long = 268_435_456,
    maxArchiveEntries: Int = 100_000,
    maxArchiveBytes: Long = 21_474_836_480,
    fileOperationRetentionMinutes: Long = 30
) = FileBrowser(
    FileBrowserConfiguration(
        enabled = true,
        access = access,
        roots = listOf(root.toString()),
        maxEditableBytes = maxEditableBytes,
        maxUploadBytes = maxUploadBytes,
        trash = trash,
        searchTimeoutSeconds = searchTimeoutSeconds,
        thumbnails = thumbnails,
        maxThumbnailSourceBytes = maxThumbnailSourceBytes,
        maxThumbnailCacheBytes = maxThumbnailCacheBytes,
        maxArchiveEntries = maxArchiveEntries,
        maxArchiveBytes = maxArchiveBytes,
        fileOperationRetentionMinutes = fileOperationRetentionMinutes
    ),
    cache
)

/**
 * Running as root makes every permission bit advisory, so a test that needs a refusal has
 * nothing to assert about. Skipping is honest where passing would not be.
 */
fun lockDirectory(directory: Path): Path {
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-xr-xr-x"))
    Assumptions.assumeFalse(Files.isWritable(directory), "This user can write anywhere, so nothing gets refused")
    return directory
}

fun unlockEverythingUnder(directory: Path) {
    Files.walk(directory).use { paths ->
        paths.forEach { path ->
            runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x")) }
        }
    }
}

fun fileEntry(
    name: String,
    path: String = "/storage/$name",
    type: FileEntryType = FileEntryType.FILE
) = FileEntry(
    name = name,
    path = path,
    type = type,
    sizeBytes = 3,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    accessedAt = Instant.EPOCH,
    mimeType = "text/plain",
    iconId = "txt",
    editable = true,
    openableAsLog = true,
    isArchive = false,
    browsableAsArchive = false,
    hasThumbnail = false,
    isHidden = false,
    permissions = "rw-r--r--",
    owner = "monitee",
    group = "monitee",
    linkTarget = null
)
