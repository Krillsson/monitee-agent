package com.krillsson.sysapi.filebrowser

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/files")
class FileIconController(private val fileTypeRegistry: FileTypeRegistry) {

    private val iconIds = fileTypeRegistry.iconIds()

    @GetMapping("/icons/{iconId}.svg", produces = ["image/svg+xml"])
    fun icon(@PathVariable iconId: String): ResponseEntity<Resource> {
        if (iconId !in iconIds) {
            return ResponseEntity.notFound().build()
        }
        val resource = ClassPathResource("file-icons/$iconId.svg")
        if (!resource.exists()) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("image/svg+xml"))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .body(resource)
    }
}
