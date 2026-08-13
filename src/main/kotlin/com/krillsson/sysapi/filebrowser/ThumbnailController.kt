package com.krillsson.sysapi.filebrowser

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files
import java.time.Duration

@RestController
@RequestMapping("/files")
class ThumbnailController(private val thumbnailService: ThumbnailService) {

    @GetMapping("/thumbnail", produces = [ThumbnailService.MEDIA_TYPE])
    fun thumbnail(
        @RequestParam path: String,
        @RequestParam(required = false) size: Int?
    ): ResponseEntity<Resource> {
        val thumbnail = thumbnailService.thumbnail(path, size)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(ThumbnailService.MEDIA_TYPE))
            .contentLength(Files.size(thumbnail))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
            .body(FileSystemResource(thumbnail))
    }
}
