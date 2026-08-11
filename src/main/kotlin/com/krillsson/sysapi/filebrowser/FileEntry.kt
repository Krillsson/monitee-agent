package com.krillsson.sysapi.filebrowser

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
    val mimeType: String?,
    val iconId: String,
    val editable: Boolean,
    val openableAsLog: Boolean
)

data class DirectoryListing(
    val entry: FileEntry,
    val entries: List<FileEntry>,
    val truncated: Boolean
)

data class TextFileContent(
    val entry: FileEntry,
    val contents: String
)
