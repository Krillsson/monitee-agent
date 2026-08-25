package com.krillsson.sysapi.filebrowser

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

class UnsupportedUploadContentTypeException(contentType: String) :
    FileBrowserException(
        "An upload cannot be sent as $contentType. Send the file as the whole request body",
        FileBrowserErrorType.NOT_SUPPORTED
    )

@RestController
@RequestMapping("/files")
class FileUploadController(private val manager: FileBrowserManager) {

    companion object {
        private val CONTENT_TYPES_THE_BODY_IS_NOT = listOf(
            "application/x-www-form-urlencoded",
            "multipart/form-data",
            "text/plain"
        )
    }

    @PostMapping("/upload")
    fun upload(
        @RequestParam directory: String,
        @RequestParam name: String,
        @RequestParam(defaultValue = "false") overwrite: Boolean,
        request: HttpServletRequest
    ): ResponseEntity<FileEntry> {
        rejectAnythingButTheFileItself(request.contentType)
        val entry = manager.upload(directory, name, overwrite, request.inputStream)
        return ResponseEntity.status(HttpStatus.CREATED).body(entry)
    }

    /**
     * A form encoded body is read by the servlet container as soon as a request parameter is asked
     * for, which would leave nothing of it to store, and a multipart body would be stored envelope
     * and all. Neither is worth guessing at, so both are refused.
     *
     * text/plain is refused for a different reason. Those three are the content types a form can
     * post without asking permission first, and the API authenticates with credentials a browser
     * attaches on its own, so leaving it open would let any page write a file here on behalf of
     * whoever is logged in. Anything else is preflighted and cannot reach this from another origin.
     */
    private fun rejectAnythingButTheFileItself(contentType: String?) {
        val given = contentType.orEmpty()
        CONTENT_TYPES_THE_BODY_IS_NOT
            .firstOrNull { given.startsWith(it, ignoreCase = true) }
            ?.let { throw UnsupportedUploadContentTypeException(it) }
    }
}
