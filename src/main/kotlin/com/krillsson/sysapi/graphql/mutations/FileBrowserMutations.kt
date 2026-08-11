package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.filebrowser.FileEntry

data class SaveTextFileInput(
    val path: String,
    val contents: String
)

data class CopyFileInput(
    val source: String,
    val destination: String
)

data class MoveFileInput(
    val source: String,
    val destination: String
)

data class DeleteFileInput(
    val path: String,
    val recursive: Boolean = false
)

data class CreateDirectoryInput(
    val path: String
)

data class SaveTextFileOutput(
    val success: Boolean,
    val reason: String?
)

data class CopyFileOutput(
    val success: Boolean,
    val reason: String?
)

data class MoveFileOutput(
    val success: Boolean,
    val reason: String?
)

data class DeleteFileOutput(
    val success: Boolean,
    val reason: String?
)

data class CreateDirectoryOutput(
    val success: Boolean,
    val reason: String?,
    val entry: FileEntry?
)
