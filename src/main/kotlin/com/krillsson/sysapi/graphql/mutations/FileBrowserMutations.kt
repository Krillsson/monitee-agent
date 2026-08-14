package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.filebrowser.FileEntry
import com.krillsson.sysapi.filebrowser.FileOperation
import com.krillsson.sysapi.filebrowser.TrashEntry

data class SaveTextFileInput(
    val path: String,
    val contents: String
)

data class CopyFileInput(
    val source: String,
    val destination: String,
    val overwrite: Boolean = false
)

data class MoveFileInput(
    val source: String,
    val destination: String,
    val overwrite: Boolean = false
)

data class CopyFilesInput(
    val sources: List<String>,
    val destinationDirectory: String,
    val overwrite: Boolean = false
)

data class MoveFilesInput(
    val sources: List<String>,
    val destinationDirectory: String,
    val overwrite: Boolean = false
)

data class DeleteFilesInput(
    val paths: List<String>,
    val recursive: Boolean = false
)

data class DeleteFileInput(
    val path: String,
    val recursive: Boolean = false
)

data class CreateDirectoryInput(
    val path: String
)

data class ExtractArchiveInput(
    val path: String,
    val destinationDirectory: String,
    val overwrite: Boolean = false,
    val entries: List<String> = emptyList()
)

data class CreateArchiveInput(
    val sources: List<String>,
    val destination: String,
    val overwrite: Boolean = false
)

data class MoveToTrashInput(
    val path: String
)

data class RestoreFromTrashInput(
    val id: String,
    val overwrite: Boolean = false
)

data class EmptyTrashInput(
    val id: String? = null
)

data class CancelFileOperationInput(
    val id: String
)

data class SaveTextFileOutput(
    val success: Boolean,
    val reason: String?
)

data class CreateDirectoryOutput(
    val success: Boolean,
    val reason: String?,
    val entry: FileEntry?
)

data class FileOperationOutput(
    val success: Boolean,
    val reason: String?,
    val operation: FileOperation?
)

data class MoveToTrashOutput(
    val success: Boolean,
    val reason: String?,
    val entry: TrashEntry?
)

data class RestoreFromTrashOutput(
    val success: Boolean,
    val reason: String?,
    val entry: FileEntry?
)

