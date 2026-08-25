package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.util.logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.io.IOException

data class FileBrowserError(val reason: String, val type: FileBrowserErrorType)

@RestControllerAdvice(
    assignableTypes = [
        FileDownloadController::class,
        FileUploadController::class,
        FileIconController::class,
        ThumbnailController::class,
        ArchiveEntryDownloadController::class
    ]
)
class FileBrowserControllerAdvice {

    val logger by logger()

    @ExceptionHandler(FileBrowserException::class)
    fun handleRefusal(ex: FileBrowserException): ResponseEntity<FileBrowserError> = respondTo(ex, ex)

    @ExceptionHandler(IOException::class)
    fun handleFailure(ex: IOException): ResponseEntity<FileBrowserError> =
        respondTo(FileBrowserErrors.describe(ex), ex)

    private fun respondTo(failure: FileBrowserException, cause: Throwable): ResponseEntity<FileBrowserError> {
        when (failure.type) {
            FileBrowserErrorType.REFUSED -> logger.info("A file browser request was refused: ${failure.message}")
            FileBrowserErrorType.IO_ERROR -> logger.error("A file browser request failed", cause)
            else -> logger.warn("A file browser request failed: ${failure.message}")
        }
        return ResponseEntity.status(statusOf(failure.type))
            .body(FileBrowserError(failure.message.orEmpty(), failure.type))
    }

    private fun statusOf(type: FileBrowserErrorType) = when (type) {
        FileBrowserErrorType.NOT_FOUND -> HttpStatus.NOT_FOUND
        FileBrowserErrorType.ALREADY_EXISTS -> HttpStatus.CONFLICT
        FileBrowserErrorType.NOT_EMPTY -> HttpStatus.CONFLICT
        FileBrowserErrorType.CANCELLED -> HttpStatus.CONFLICT
        FileBrowserErrorType.PERMISSION_DENIED -> HttpStatus.FORBIDDEN
        FileBrowserErrorType.READ_ONLY_FILE_SYSTEM -> HttpStatus.FORBIDDEN
        FileBrowserErrorType.OUT_OF_SPACE -> HttpStatus.INSUFFICIENT_STORAGE
        FileBrowserErrorType.NOT_SUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE
        FileBrowserErrorType.IO_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
        FileBrowserErrorType.REFUSED -> HttpStatus.BAD_REQUEST
    }
}
