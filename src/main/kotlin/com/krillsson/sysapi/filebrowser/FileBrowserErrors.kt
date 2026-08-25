package com.krillsson.sysapi.filebrowser

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.name

enum class FileBrowserErrorType {
    REFUSED,
    NOT_FOUND,
    ALREADY_EXISTS,
    NOT_EMPTY,
    PERMISSION_DENIED,
    OUT_OF_SPACE,
    READ_ONLY_FILE_SYSTEM,
    NOT_SUPPORTED,
    CANCELLED,
    IO_ERROR;

    val stopsABatch get() = this == OUT_OF_SPACE || this == READ_ONLY_FILE_SYSTEM
}

open class FileBrowserException(
    message: String,
    val type: FileBrowserErrorType = FileBrowserErrorType.REFUSED
) : RuntimeException(message)

class FileAlreadyThereException(message: String) :
    FileBrowserException(message, FileBrowserErrorType.ALREADY_EXISTS)

class UnsupportedThumbnailException(message: String) :
    FileBrowserException(message, FileBrowserErrorType.NOT_SUPPORTED)

object FileBrowserErrors {

    private val OUT_OF_SPACE_REASONS = listOf("no space left", "quota exceeded")

    private const val READ_ONLY_REASON = "read-only file system"

    val runningAs: String get() = System.getProperty("user.name") ?: "an unknown user"

    /**
     * Neither running out of space nor writing to a read-only mount has an exception of its own.
     * ENOSPC, EDQUOT and EROFS all arrive as a plain FileSystemException, or as a bare IOException
     * when the write went through a stream, carrying nothing but the C library's message for the
     * errno. Matching that text is the only way to tell them apart, so an unclassified write
     * failure asks the file store how much room is left as well.
     */
    fun describe(ex: Throwable, writingTo: Path? = null): FileBrowserException {
        if (ex is FileBrowserException) {
            return ex
        }
        val where = writingTo?.let { nearest(it) } ?: pathIn(ex)
        val reason = reasonOf(ex)
        return when {
            ex is AccessDeniedException -> FileBrowserException(
                if (writingTo == null) {
                    about(where, { "$it cannot be opened by the agent" }, "The agent cannot open it") +
                        ", which runs as $runningAs"
                } else {
                    "The agent is not allowed to write to ${where ?: writingTo}. It runs as $runningAs"
                },
                FileBrowserErrorType.PERMISSION_DENIED
            )

            ex is NoSuchFileException -> FileBrowserException(
                about(where, { "$it does not exist" }, "It does not exist"),
                FileBrowserErrorType.NOT_FOUND
            )

            ex is FileAlreadyExistsException -> FileAlreadyThereException(
                about(where, { "$it already exists" }, "It already exists")
            )

            ex is DirectoryNotEmptyException -> FileBrowserException(
                about(where, { "$it is not empty" }, "It is not empty"),
                FileBrowserErrorType.NOT_EMPTY
            )

            OUT_OF_SPACE_REASONS.any { reason.contains(it, ignoreCase = true) } ||
                (ex is IOException && freeBytesOn(where) == 0L) -> FileBrowserException(
                about(
                    where,
                    { "There is no space left on the volume holding $it" },
                    "There is no space left on the volume being written to"
                ),
                FileBrowserErrorType.OUT_OF_SPACE
            )

            reason.contains(READ_ONLY_REASON, ignoreCase = true) -> FileBrowserException(
                about(
                    where,
                    { "$it is on a file system that is mounted read-only" },
                    "The file system being written to is mounted read-only"
                ),
                FileBrowserErrorType.READ_ONLY_FILE_SYSTEM
            )

            else -> FileBrowserException(
                about(where, { "$it could not be written: $reason" }, reason),
                FileBrowserErrorType.IO_ERROR
            )
        }
    }

    private fun about(where: Path?, named: (Path) -> String, unnamed: String): String =
        where?.let(named) ?: unnamed

    fun freeBytesOn(path: Path?): Long? {
        val existing = path?.let { nearest(it) } ?: return null
        return runCatching { Files.getFileStore(existing).usableSpace }.getOrNull()
    }

    /**
     * A copy writes through a temporary file, so the path the file system complained about is a
     * name the caller never chose and would not recognise. Its directory is what they asked for.
     */
    private fun pathIn(ex: Throwable): Path? {
        val named = (ex as? FileSystemException)?.let { it.otherFile ?: it.file } ?: return null
        val path = runCatching { Path.of(named) }.getOrNull() ?: return null
        return if (isTemporary(path)) path.parent else path
    }

    private fun isTemporary(path: Path) = path.name.startsWith(FileBrowserManager.TEMP_FILE_PREFIX) &&
        path.name.endsWith(FileBrowserManager.TEMP_FILE_SUFFIX)

    private fun nearest(path: Path): Path? =
        generateSequence(path) { it.parent }.firstOrNull { Files.exists(it) }

    private fun reasonOf(ex: Throwable): String =
        (ex as? FileSystemException)?.reason ?: ex.message ?: requireNotNull(ex::class.simpleName)
}
