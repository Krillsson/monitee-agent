package com.krillsson.sysapi.filebrowser

import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/files")
class ArchiveEntryDownloadController(
    private val archiveBrowser: ArchiveBrowser,
    private val fileTypeRegistry: FileTypeRegistry
) {

    @GetMapping("/archive/download")
    fun download(
        @RequestParam path: String,
        @RequestParam entry: String
    ): ResponseEntity<StreamingResponseBody> {
        val found = archiveBrowser.entryOf(path, entry)
        val disposition = ContentDisposition.attachment()
            .filename(found.name, StandardCharsets.UTF_8)
            .build()
        val body = StreamingResponseBody { output ->
            archiveBrowser.readEntry(path, entry) { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        return ResponseEntity.ok()
            .header("Content-Disposition", disposition.toString())
            .contentType(MediaType.parseMediaType(fileTypeRegistry.mimeTypeOf(found.name)))
            .contentLength(found.sizeBytes)
            .body(body)
    }
}
