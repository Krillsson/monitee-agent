package com.krillsson.sysapi.filebrowser

import java.time.Instant

enum class FileOperationType {
    COPY,
    MOVE,
    DELETE,
    EXTRACT_ARCHIVE,
    CREATE_ARCHIVE,
    EMPTY_TRASH
}

enum class FileOperationState {
    QUEUED,
    MEASURING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class FileOperation(
    val id: String,
    val type: FileOperationType,
    val state: FileOperationState,
    val paths: List<String>,
    val destination: String?,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val currentPath: String?,
    val processedFiles: Int,
    val totalFiles: Int?,
    val processedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long?,
    val successes: List<String>,
    val failures: List<FileOperationFailure>,
    val reason: String?
)

/**
 * What a walk of the source paths found, for an operation that could not know its totals up
 * front. Bytes is null when the operation does not count them at all (a delete), not when the
 * walk gave up on a large tree - that case reports no PathTotals rather than a wrong number.
 */
data class PathTotals(val files: Int, val bytes: Long?)

class FileOperationCancelledException : FileBrowserException("The operation was cancelled")

/**
 * What the working end of an operation reports back through. The no-op implementation is what
 * the manager uses when something calls it outside of an operation, so the same code paths serve
 * both.
 */
interface FileOperationSink {

    fun beginFile(path: String, sizeBytes: Long)

    fun addBytes(bytes: Long)

    fun fileDone()

    fun succeeded(path: String)

    fun failed(path: String, reason: String)

    fun isCancelled(): Boolean

    fun requireNotCancelled() {
        if (isCancelled()) {
            throw FileOperationCancelledException()
        }
    }
}

object NoFileOperationSink : FileOperationSink {

    override fun beginFile(path: String, sizeBytes: Long) = Unit

    override fun addBytes(bytes: Long) = Unit

    override fun fileDone() = Unit

    override fun succeeded(path: String) = Unit

    override fun failed(path: String, reason: String) = Unit

    override fun isCancelled() = false
}
