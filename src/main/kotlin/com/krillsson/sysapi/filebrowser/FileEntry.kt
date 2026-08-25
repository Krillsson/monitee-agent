package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.graphql.domain.PageInfo
import java.time.Instant

enum class FileEntryType {
    FILE,
    DIRECTORY,
    SYMLINK,
    OTHER
}

data class FileEntry(
    val name: String,
    val path: String,
    val type: FileEntryType,
    val sizeBytes: Long,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val accessedAt: Instant?,
    val mimeType: String?,
    val iconId: String,
    val editable: Boolean,
    val openableAsLog: Boolean,
    val isArchive: Boolean,
    val browsableAsArchive: Boolean,
    val hasThumbnail: Boolean,
    val isHidden: Boolean,
    val permissions: String?,
    val owner: String?,
    val group: String?,
    val linkTarget: String?
)

data class DirectoryListing(
    val entry: FileEntry,
    val entries: List<FileEntry>,
    val truncated: Boolean
)

data class FileBrowserLimits(
    val maxEditableBytes: Long,
    val maxUploadBytes: Long,
    val maxLogViewBytes: Long,
    val searchTimeoutSeconds: Long,
    val thumbnails: Boolean,
    val maxThumbnailSourceBytes: Long,
    val maxArchiveEntries: Int,
    val maxArchiveBytes: Long,
    val fileOperationRetentionMinutes: Long
)

data class FileEntryEdge(
    val cursor: String,
    val node: FileEntry
)

data class DirectoryListingConnection(
    val entry: FileEntry,
    val edges: List<FileEntryEdge>,
    val pageInfo: PageInfo,
    val totalCount: Int
)

data class TextFileContent(
    val entry: FileEntry,
    val contents: String
)

data class FileSearchResult(
    val entries: List<FileEntry>,
    val truncated: Boolean,
    val durationMillis: Long
)

data class DirectorySize(
    val entry: FileEntry,
    val totalBytes: Long,
    val fileCount: Int,
    val directoryCount: Int,
    val truncated: Boolean
)

data class ArchiveEntry(
    val name: String,
    val entryPath: String,
    val type: FileEntryType,
    val sizeBytes: Long,
    val compressedBytes: Long,
    val updatedAt: Instant?,
    val mimeType: String?,
    val iconId: String
)

data class ArchiveListing(
    val entry: FileEntry,
    val entryPath: String,
    val entries: List<ArchiveEntry>,
    val truncated: Boolean
)

data class FileOperationFailure(
    val path: String,
    val reason: String,
    val type: FileBrowserErrorType
)

data class TrashEntry(
    val id: String,
    val entry: FileEntry,
    val originalPath: String,
    val deletedAt: Instant?,
    val root: String
)
