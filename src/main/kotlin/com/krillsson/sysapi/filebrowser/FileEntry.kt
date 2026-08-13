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

data class FileOperationFailure(
    val path: String,
    val reason: String
)

data class BatchFileOutcome(
    val successes: List<String>,
    val failures: List<FileOperationFailure>
)

data class TrashEntry(
    val id: String,
    val entry: FileEntry,
    val originalPath: String,
    val deletedAt: Instant?,
    val root: String
)
