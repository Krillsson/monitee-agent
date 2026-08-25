package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.filebrowser.FileBrowserErrorType
import com.krillsson.sysapi.filebrowser.FileBrowserErrors
import com.krillsson.sysapi.filebrowser.FileBrowserManager
import com.krillsson.sysapi.filebrowser.FileOperationService
import com.krillsson.sysapi.filebrowser.TrashService
import com.krillsson.sysapi.graphql.mutations.CancelFileOperationInput
import com.krillsson.sysapi.graphql.mutations.CopyFileInput
import com.krillsson.sysapi.graphql.mutations.CopyFilesInput
import com.krillsson.sysapi.graphql.mutations.CreateArchiveInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryOutput
import com.krillsson.sysapi.graphql.mutations.DeleteFileInput
import com.krillsson.sysapi.graphql.mutations.DeleteFilesInput
import com.krillsson.sysapi.graphql.mutations.EmptyTrashInput
import com.krillsson.sysapi.graphql.mutations.ExtractArchiveInput
import com.krillsson.sysapi.graphql.mutations.FileOperationOutput
import com.krillsson.sysapi.graphql.mutations.MoveFileInput
import com.krillsson.sysapi.graphql.mutations.MoveFilesInput
import com.krillsson.sysapi.graphql.mutations.MoveToTrashInput
import com.krillsson.sysapi.graphql.mutations.MoveToTrashOutput
import com.krillsson.sysapi.graphql.mutations.RestoreFromTrashInput
import com.krillsson.sysapi.graphql.mutations.RestoreFromTrashOutput
import com.krillsson.sysapi.graphql.mutations.SaveTextFileInput
import com.krillsson.sysapi.graphql.mutations.SaveTextFileOutput
import com.krillsson.sysapi.util.logger
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class FileBrowserMutationResolver(
    private val manager: FileBrowserManager,
    private val operations: FileOperationService,
    private val trashService: TrashService
) {

    val logger by logger()

    @MutationMapping
    fun saveTextFile(@Argument input: SaveTextFileInput): SaveTextFileOutput =
        attempt("Saving ${input.path}", { reason, type -> SaveTextFileOutput(false, reason, type) }) {
            manager.saveTextFile(input.path, input.contents)
            SaveTextFileOutput(true, null, null)
        }

    @MutationMapping
    fun copyFile(@Argument input: CopyFileInput): FileOperationOutput =
        started("Copying ${input.source}") { operations.copy(input.source, input.destination, input.overwrite) }

    @MutationMapping
    fun moveFile(@Argument input: MoveFileInput): FileOperationOutput =
        started("Moving ${input.source}") { operations.move(input.source, input.destination, input.overwrite) }

    @MutationMapping
    fun deleteFile(@Argument input: DeleteFileInput): FileOperationOutput =
        started("Deleting ${input.path}") { operations.delete(input.path, input.recursive) }

    @MutationMapping
    fun createDirectory(@Argument input: CreateDirectoryInput): CreateDirectoryOutput =
        attempt("Creating ${input.path}", { reason, type -> CreateDirectoryOutput(false, reason, type, null) }) {
            CreateDirectoryOutput(true, null, null, manager.createDirectory(input.path))
        }

    @MutationMapping
    fun copyFiles(@Argument input: CopyFilesInput): FileOperationOutput =
        started("Copying ${input.sources.size} paths") {
            operations.copyAll(input.sources, input.destinationDirectory, input.overwrite)
        }

    @MutationMapping
    fun moveFiles(@Argument input: MoveFilesInput): FileOperationOutput =
        started("Moving ${input.sources.size} paths") {
            operations.moveAll(input.sources, input.destinationDirectory, input.overwrite)
        }

    @MutationMapping
    fun deleteFiles(@Argument input: DeleteFilesInput): FileOperationOutput =
        started("Deleting ${input.paths.size} paths") { operations.deleteAll(input.paths, input.recursive) }

    @MutationMapping
    fun extractArchive(@Argument input: ExtractArchiveInput): FileOperationOutput =
        started("Extracting ${input.path}") {
            operations.extractArchive(input.path, input.destinationDirectory, input.overwrite, input.entries)
        }

    @MutationMapping
    fun createArchive(@Argument input: CreateArchiveInput): FileOperationOutput =
        started("Archiving into ${input.destination}") {
            operations.createArchive(input.sources, input.destination, input.overwrite)
        }

    @MutationMapping
    fun cancelFileOperation(@Argument input: CancelFileOperationInput): FileOperationOutput =
        started("Cancelling ${input.id}") { operations.cancel(input.id) }

    @MutationMapping
    fun moveToTrash(@Argument input: MoveToTrashInput): MoveToTrashOutput =
        attempt("Trashing ${input.path}", { reason, type -> MoveToTrashOutput(false, reason, type, null) }) {
            MoveToTrashOutput(true, null, null, trashService.trash(input.path))
        }

    @MutationMapping
    fun restoreFromTrash(@Argument input: RestoreFromTrashInput): RestoreFromTrashOutput =
        attempt("Restoring ${input.id}", { reason, type -> RestoreFromTrashOutput(false, reason, type, null) }) {
            RestoreFromTrashOutput(true, null, null, trashService.restore(input.id, input.overwrite))
        }

    @MutationMapping
    fun emptyTrash(@Argument input: EmptyTrashInput): FileOperationOutput =
        started("Emptying the trash") { operations.emptyTrash(input.id) }

    private fun started(operation: String, start: () -> com.krillsson.sysapi.filebrowser.FileOperation) =
        attempt(operation, { reason, type -> FileOperationOutput(false, reason, type, null) }) {
            FileOperationOutput(true, null, null, start())
        }

    private fun <T> attempt(
        operation: String,
        failed: (String, FileBrowserErrorType) -> T,
        action: () -> T
    ): T {
        return try {
            action()
        } catch (ex: Exception) {
            val failure = FileBrowserErrors.describe(ex)
            when (failure.type) {
                FileBrowserErrorType.REFUSED -> logger.info("$operation was refused: ${failure.message}")
                FileBrowserErrorType.IO_ERROR -> logger.error("$operation failed", ex)
                else -> logger.warn("$operation failed: ${failure.message}")
            }
            failed(failure.message.orEmpty(), failure.type)
        }
    }
}
