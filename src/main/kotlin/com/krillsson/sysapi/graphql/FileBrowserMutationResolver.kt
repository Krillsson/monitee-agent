package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.filebrowser.ArchiveService
import com.krillsson.sysapi.filebrowser.BatchFileOutcome
import com.krillsson.sysapi.filebrowser.FileBrowserException
import com.krillsson.sysapi.filebrowser.FileBrowserManager
import com.krillsson.sysapi.filebrowser.TrashService
import com.krillsson.sysapi.graphql.mutations.BatchFileOutput
import com.krillsson.sysapi.graphql.mutations.CopyFileInput
import com.krillsson.sysapi.graphql.mutations.CopyFileOutput
import com.krillsson.sysapi.graphql.mutations.CopyFilesInput
import com.krillsson.sysapi.graphql.mutations.CreateArchiveInput
import com.krillsson.sysapi.graphql.mutations.CreateArchiveOutput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryOutput
import com.krillsson.sysapi.graphql.mutations.DeleteFileInput
import com.krillsson.sysapi.graphql.mutations.DeleteFileOutput
import com.krillsson.sysapi.graphql.mutations.DeleteFilesInput
import com.krillsson.sysapi.graphql.mutations.EmptyTrashInput
import com.krillsson.sysapi.graphql.mutations.EmptyTrashOutput
import com.krillsson.sysapi.graphql.mutations.ExtractArchiveInput
import com.krillsson.sysapi.graphql.mutations.ExtractArchiveOutput
import com.krillsson.sysapi.graphql.mutations.MoveFileInput
import com.krillsson.sysapi.graphql.mutations.MoveFileOutput
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
    private val archiveService: ArchiveService,
    private val trashService: TrashService
) {

    val logger by logger()

    @MutationMapping
    fun saveTextFile(@Argument input: SaveTextFileInput): SaveTextFileOutput =
        attempt("Saving ${input.path}", { SaveTextFileOutput(false, it) }) {
            manager.saveTextFile(input.path, input.contents)
            SaveTextFileOutput(true, null)
        }

    @MutationMapping
    fun copyFile(@Argument input: CopyFileInput): CopyFileOutput =
        attempt("Copying ${input.source}", { CopyFileOutput(false, it) }) {
            manager.copy(input.source, input.destination, input.overwrite)
            CopyFileOutput(true, null)
        }

    @MutationMapping
    fun moveFile(@Argument input: MoveFileInput): MoveFileOutput =
        attempt("Moving ${input.source}", { MoveFileOutput(false, it) }) {
            manager.move(input.source, input.destination, input.overwrite)
            MoveFileOutput(true, null)
        }

    @MutationMapping
    fun deleteFile(@Argument input: DeleteFileInput): DeleteFileOutput =
        attempt("Deleting ${input.path}", { DeleteFileOutput(false, it) }) {
            manager.delete(input.path, input.recursive)
            DeleteFileOutput(true, null)
        }

    @MutationMapping
    fun createDirectory(@Argument input: CreateDirectoryInput): CreateDirectoryOutput =
        attempt("Creating ${input.path}", { CreateDirectoryOutput(false, it, null) }) {
            CreateDirectoryOutput(true, null, manager.createDirectory(input.path))
        }

    @MutationMapping
    fun copyFiles(@Argument input: CopyFilesInput): BatchFileOutput =
        attempt("Copying ${input.sources.size} paths", { refusedBatch(it) }) {
            manager.copyAll(input.sources, input.destinationDirectory, input.overwrite).asOutput()
        }

    @MutationMapping
    fun moveFiles(@Argument input: MoveFilesInput): BatchFileOutput =
        attempt("Moving ${input.sources.size} paths", { refusedBatch(it) }) {
            manager.moveAll(input.sources, input.destinationDirectory, input.overwrite).asOutput()
        }

    @MutationMapping
    fun deleteFiles(@Argument input: DeleteFilesInput): BatchFileOutput =
        attempt("Deleting ${input.paths.size} paths", { refusedBatch(it) }) {
            manager.deleteAll(input.paths, input.recursive).asOutput()
        }

    @MutationMapping
    fun extractArchive(@Argument input: ExtractArchiveInput): ExtractArchiveOutput =
        attempt("Extracting ${input.path}", { ExtractArchiveOutput(false, it, null, 0, 0) }) {
            val extraction = archiveService.extract(input.path, input.destinationDirectory, input.overwrite)
            ExtractArchiveOutput(true, null, extraction.entry, extraction.entryCount, extraction.totalBytes)
        }

    @MutationMapping
    fun createArchive(@Argument input: CreateArchiveInput): CreateArchiveOutput =
        attempt("Archiving into ${input.destination}", { CreateArchiveOutput(false, it, null) }) {
            CreateArchiveOutput(true, null, archiveService.create(input.sources, input.destination, input.overwrite))
        }

    @MutationMapping
    fun moveToTrash(@Argument input: MoveToTrashInput): MoveToTrashOutput =
        attempt("Trashing ${input.path}", { MoveToTrashOutput(false, it, null) }) {
            MoveToTrashOutput(true, null, trashService.trash(input.path))
        }

    @MutationMapping
    fun restoreFromTrash(@Argument input: RestoreFromTrashInput): RestoreFromTrashOutput =
        attempt("Restoring ${input.id}", { RestoreFromTrashOutput(false, it, null) }) {
            RestoreFromTrashOutput(true, null, trashService.restore(input.id, input.overwrite))
        }

    @MutationMapping
    fun emptyTrash(@Argument input: EmptyTrashInput): EmptyTrashOutput =
        attempt("Emptying the trash", { EmptyTrashOutput(false, it, 0) }) {
            EmptyTrashOutput(true, null, trashService.empty(input.id))
        }

    private fun BatchFileOutcome.asOutput() =
        BatchFileOutput(failures.isEmpty(), null, successes, failures)

    private fun refusedBatch(reason: String) = BatchFileOutput(false, reason, emptyList(), emptyList())

    private fun <T> attempt(operation: String, failed: (String) -> T, action: () -> T): T {
        return try {
            action()
        } catch (ex: FileBrowserException) {
            logger.info("$operation was refused: ${ex.message}")
            failed(ex.message.orEmpty())
        } catch (ex: Exception) {
            logger.error("$operation failed", ex)
            failed(ex.message ?: requireNotNull(ex::class.simpleName))
        }
    }
}
