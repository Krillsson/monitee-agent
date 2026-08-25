package com.krillsson.sysapi.filebrowser

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class FileTypeRegistryTest {

    private val registry = FileTypeRegistry()

    @ParameterizedTest
    @CsvSource(
        "holiday.JPG, image/jpeg, jpg",
        "movie.mkv, video/x-matroska, mkv",
        "song.flac, audio/flac, flac",
        "backup.tar.gz, application/gzip, gz",
        "notes.md, text/markdown, md",
        "agent.log, text/plain, log",
        "configuration.yml, application/yaml, yml"
    )
    fun `maps a known extension to its mime type and icon`(name: String, mimeType: String, iconId: String) {
        // Given
        val given = name

        // When
        val type = registry.typeOf(given)

        // Then
        type.shouldNotBeNull().mimeType shouldBe mimeType
        registry.iconIdOf(given) shouldBe iconId
    }

    @ParameterizedTest
    @CsvSource(
        "holiday.arw, blank",
        "recording.m2ts, blank",
        "archive.zst, blank",
        "README, blank"
    )
    fun `falls back for an extension it does not know`(name: String, iconId: String) {
        // Given
        val given = name

        // When
        val type = registry.typeOf(given)

        // Then
        type shouldBe null
        registry.mimeTypeOf(given) shouldBe "application/octet-stream"
        registry.iconIdOf(given) shouldBe iconId
    }

    @Test
    fun `every icon it can name is on the classpath`() {
        // Given
        val iconIds = registry.iconIds()

        // When
        val missing = iconIds.filter { javaClass.getResource("/file-icons/$it.svg") == null }

        // Then
        missing shouldBe emptyList()
    }

    @ParameterizedTest
    @ValueSource(strings = ["agent.log", "cron.out", "syslog"])
    fun `recognises the files the log viewer can open`(name: String) {
        // Given
        val given = name

        // When
        val result = registry.looksLikeALogFile(given)

        // Then
        result shouldBe true
    }

    @ParameterizedTest
    @ValueSource(strings = ["notes.txt", "movie.mkv", "archive.zip", "photo.jpg"])
    fun `does not offer the log viewer for anything else`(name: String) {
        // Given
        val given = name

        // When
        val result = registry.looksLikeALogFile(given)

        // Then
        result shouldBe false
    }

    @ParameterizedTest
    @CsvSource(
        "bundle.zip, ZIP",
        "backup.tar, TAR",
        "backup.tar.gz, TAR_GZ",
        "backup.TGZ, TAR_GZ",
        "syslog.log.gz, GZIP"
    )
    fun `reads the archive format out of the name`(name: String, format: ArchiveFormat) {
        // Given
        val given = name

        // When
        val result = registry.archiveFormatOf(given)

        // Then
        result shouldBe format
    }

    @ParameterizedTest
    @ValueSource(strings = ["photo.jpg", "notes.txt", "movie.mkv", ".gz", "bundle.zipper"])
    fun `does not offer to extract anything else`(name: String) {
        // Given
        val given = name

        // When
        val result = registry.archiveFormatOf(given)

        // Then
        result shouldBe null
    }

    @ParameterizedTest
    @CsvSource(
        "holiday.JPG, true",
        "sketch.png, true",
        "animation.gif, true",
        "raw.cr2, false",
        "movie.mkv, false",
        "notes.txt, false"
    )
    fun `only offers a thumbnail for the image formats it can decode`(name: String, expected: Boolean) {
        // Given
        val given = name

        // When
        val result = registry.isThumbnailable(given)

        // Then
        result shouldBe expected
    }
}
