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
import java.nio.file.Files
import kotlin.io.path.name

@RestController
@RequestMapping("/files")
class FileDownloadController(
    private val manager: FileBrowserManager,
    private val fileTypeRegistry: FileTypeRegistry
) {

    @GetMapping("/download")
    fun download(@RequestParam path: String): ResponseEntity<StreamingResponseBody> {
        val file = manager.openForDownload(path)
        val disposition = ContentDisposition.attachment()
            .filename(file.name, StandardCharsets.UTF_8)
            .build()
        val body = StreamingResponseBody { output ->
            Files.newInputStream(file).use { it.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        return ResponseEntity.ok()
            .header("Content-Disposition", disposition.toString())
            .contentType(MediaType.parseMediaType(fileTypeRegistry.mimeTypeOf(file.name)))
            .contentLength(Files.size(file))
            .body(body)
    }
}
