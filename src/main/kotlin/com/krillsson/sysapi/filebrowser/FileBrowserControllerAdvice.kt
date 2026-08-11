package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.util.logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.io.IOException

data class FileBrowserError(val reason: String)

@RestControllerAdvice(
    assignableTypes = [
        FileDownloadController::class,
        FileUploadController::class,
        FileIconController::class
    ]
)
class FileBrowserControllerAdvice {

    val logger by logger()

    @ExceptionHandler(FileAlreadyThereException::class)
    fun handleConflict(ex: FileAlreadyThereException): ResponseEntity<FileBrowserError> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(FileBrowserError(ex.message.orEmpty()))
    }

    @ExceptionHandler(UnsupportedUploadContentTypeException::class)
    fun handleUnsupportedContentType(ex: UnsupportedUploadContentTypeException): ResponseEntity<FileBrowserError> {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(FileBrowserError(ex.message.orEmpty()))
    }

    @ExceptionHandler(FileBrowserException::class)
    fun handleRefusal(ex: FileBrowserException): ResponseEntity<FileBrowserError> {
        logger.info("A file browser request was refused: ${ex.message}")
        return ResponseEntity.badRequest().body(FileBrowserError(ex.message.orEmpty()))
    }

    @ExceptionHandler(IOException::class)
    fun handleFailure(ex: IOException): ResponseEntity<FileBrowserError> {
        logger.error("A file browser request failed", ex)
        return ResponseEntity.internalServerError()
            .body(FileBrowserError(ex.message ?: requireNotNull(ex::class.simpleName)))
    }
}
