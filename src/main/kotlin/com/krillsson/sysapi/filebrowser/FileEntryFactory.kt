package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import kotlin.io.path.name

@Service
class FileEntryFactory(
    private val configuration: FileBrowserConfiguration,
    private val fileTypeRegistry: FileTypeRegistry
) {

    fun entryOf(path: Path): FileEntry {
        val attributes = attributesOf(path)
        val posix = attributes as? PosixFileAttributes
        val type = typeOf(attributes)
        val name = path.name.ifEmpty { path.toString() }
        val size = if (type == FileEntryType.FILE) attributes?.size() ?: 0L else 0L
        return FileEntry(
            name = name,
            path = path.toString(),
            type = type,
            sizeBytes = size,
            createdAt = attributes?.creationTime()?.toInstant()?.takeIf { it != Instant.EPOCH },
            updatedAt = attributes?.lastModifiedTime()?.toInstant(),
            accessedAt = attributes?.lastAccessTime()?.toInstant()?.takeIf { it != Instant.EPOCH },
            mimeType = if (type == FileEntryType.FILE) fileTypeRegistry.mimeTypeOf(name) else null,
            iconId = if (type == FileEntryType.DIRECTORY) FileTypeRegistry.FOLDER_ICON else fileTypeRegistry.iconIdOf(
                name
            ),
            editable = type == FileEntryType.FILE &&
                    fileTypeRegistry.isTextual(name) &&
                    size <= configuration.maxEditableBytes,
            openableAsLog = type == FileEntryType.FILE &&
                    fileTypeRegistry.looksLikeALogFile(name) &&
                    size <= configuration.maxLogViewBytes,
            isArchive = type == FileEntryType.FILE && fileTypeRegistry.archiveFormatOf(name) != null,
            browsableAsArchive = type == FileEntryType.FILE &&
                    fileTypeRegistry.archiveFormatOf(name) == ArchiveFormat.ZIP,
            hasThumbnail = type == FileEntryType.FILE &&
                    configuration.thumbnails &&
                    ThumbnailSupport.available &&
                    fileTypeRegistry.isThumbnailable(name) &&
                    size <= configuration.maxThumbnailSourceBytes,
            isHidden = name.startsWith("."),
            permissions = posix?.permissions()?.let { PosixFilePermissions.toString(it) },
            owner = runCatching { posix?.owner()?.name }.getOrNull(),
            group = runCatching { posix?.group()?.name }.getOrNull(),
            linkTarget = if (type == FileEntryType.SYMLINK) linkTargetOf(path) else null
        )
    }

    fun typeOf(attributes: BasicFileAttributes?) = when {
        attributes == null -> FileEntryType.OTHER
        attributes.isSymbolicLink -> FileEntryType.SYMLINK
        attributes.isDirectory -> FileEntryType.DIRECTORY
        attributes.isRegularFile -> FileEntryType.FILE
        else -> FileEntryType.OTHER
    }

    fun attributesOf(path: Path): BasicFileAttributes? = runCatching {
        Files.readAttributes(path, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    }.getOrElse {
        runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull()
    }

    private fun linkTargetOf(path: Path): String? = runCatching { Files.readSymbolicLink(path).toString() }.getOrNull()
}
