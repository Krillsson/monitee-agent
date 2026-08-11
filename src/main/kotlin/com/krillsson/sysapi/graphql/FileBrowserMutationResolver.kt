package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.filebrowser.FileBrowserException
import com.krillsson.sysapi.filebrowser.FileBrowserManager
import com.krillsson.sysapi.graphql.mutations.CopyFileInput
import com.krillsson.sysapi.graphql.mutations.CopyFileOutput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryInput
import com.krillsson.sysapi.graphql.mutations.CreateDirectoryOutput
import com.krillsson.sysapi.graphql.mutations.DeleteFileInput
import com.krillsson.sysapi.graphql.mutations.DeleteFileOutput
import com.krillsson.sysapi.graphql.mutations.MoveFileInput
import com.krillsson.sysapi.graphql.mutations.MoveFileOutput
import com.krillsson.sysapi.graphql.mutations.SaveTextFileInput
import com.krillsson.sysapi.graphql.mutations.SaveTextFileOutput
import com.krillsson.sysapi.util.logger
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller

@Controller
class FileBrowserMutationResolver(private val manager: FileBrowserManager) {

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
            manager.copy(input.source, input.destination)
            CopyFileOutput(true, null)
        }

    @MutationMapping
    fun moveFile(@Argument input: MoveFileInput): MoveFileOutput =
        attempt("Moving ${input.source}", { MoveFileOutput(false, it) }) {
            manager.move(input.source, input.destination)
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
