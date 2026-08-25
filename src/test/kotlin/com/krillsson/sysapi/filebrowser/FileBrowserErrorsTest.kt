package com.krillsson.sysapi.filebrowser

import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class FileBrowserErrorsTest {

    @TempDir
    lateinit var tempDir: Path

    @ParameterizedTest
    @CsvSource(
        "No space left on device, OUT_OF_SPACE",
        "Disk quota exceeded, OUT_OF_SPACE",
        "Read-only file system, READ_ONLY_FILE_SYSTEM",
        "Structure needs cleaning, IO_ERROR"
    )
    fun `reads the errno the file system reported out of the only place it puts it`(
        reason: String,
        expected: FileBrowserErrorType
    ) {
        // Given
        val target = tempDir.resolve("copy.bin")

        // When
        val described = FileBrowserErrors.describe(FileSystemException(target.toString(), null, reason), target)

        // Then
        described.type shouldBe expected
    }

    @Test
    fun `classifies a stream write that ran out of space, which arrives without a file system exception`() {
        // Given
        val target = tempDir.resolve("copy.bin")

        // When
        val described = FileBrowserErrors.describe(IOException("No space left on device"), target)

        // Then
        described.type shouldBe FileBrowserErrorType.OUT_OF_SPACE
        described.message shouldContain tempDir.toString()
    }

    @Test
    fun `says who the agent is when it is not allowed to write somewhere`() {
        // Given
        val source = tempDir.resolve("notes.txt")
        val target = tempDir.resolve("locked/notes.txt")

        // When
        val described = FileBrowserErrors.describe(
            AccessDeniedException(source.toString(), target.toString(), null),
            target
        )

        // Then
        described.type shouldBe FileBrowserErrorType.PERMISSION_DENIED
        described.message shouldContain tempDir.toString()
        described.message shouldContain FileBrowserErrors.runningAs
    }

    @Test
    fun `never reports the temporary file a copy writes through, only the directory it is in`() {
        // Given
        val temp = tempDir.resolve("${FileBrowserManager.TEMP_FILE_PREFIX}123${FileBrowserManager.TEMP_FILE_SUFFIX}")

        // When
        val described = FileBrowserErrors.describe(AccessDeniedException(temp.toString()))

        // Then
        described.type shouldBe FileBrowserErrorType.PERMISSION_DENIED
        described.message shouldNotContain FileBrowserManager.TEMP_FILE_PREFIX
        described.message shouldContain tempDir.toString()
    }

    @Test
    fun `reports the destination of a move rather than only the two paths java hands over`() {
        // Given
        val source = tempDir.resolve("notes.txt")
        val destination = tempDir.resolve("locked/notes.txt")
        val thrown = AccessDeniedException(source.toString(), destination.toString(), null)

        // When
        val described = FileBrowserErrors.describe(thrown, destination)

        // Then
        described.message shouldNotContain "->"
        described.message shouldContain "not allowed to write"
    }

    @Test
    fun `keeps a refusal the file browser raised itself instead of describing it again`() {
        // Given
        val refusal = FileBrowserException("/storage is one of the configured roots and cannot be deleted")

        // When
        val described = FileBrowserErrors.describe(refusal, tempDir)

        // Then
        described shouldBe refusal
        described.type shouldBe FileBrowserErrorType.REFUSED
    }

    @Test
    fun `maps the file system exceptions that do carry a type of their own`() {
        // Given
        val path = tempDir.resolve("thing").toString()

        // When / Then
        FileBrowserErrors.describe(NoSuchFileException(path)).type shouldBe FileBrowserErrorType.NOT_FOUND
        FileBrowserErrors.describe(FileAlreadyExistsException(path)).type shouldBe FileBrowserErrorType.ALREADY_EXISTS
        FileBrowserErrors.describe(DirectoryNotEmptyException(path)).type shouldBe FileBrowserErrorType.NOT_EMPTY
    }

    @Test
    fun `falls back to a readable message when nothing named a path`() {
        // Given
        val thrown = IOException("connection reset")

        // When
        val described = FileBrowserErrors.describe(thrown)

        // Then
        described.type shouldBe FileBrowserErrorType.IO_ERROR
        described.message shouldBe "connection reset"
    }

    @Test
    fun `reads the free space of the volume a destination that does not exist yet would land on`() {
        // Given
        val target = tempDir.resolve("not/created/yet.bin")

        // When
        val free = FileBrowserErrors.freeBytesOn(target)

        // Then
        free.shouldNotBeNull() shouldBeGreaterThan 0L
    }

    @ParameterizedTest
    @CsvSource(
        "OUT_OF_SPACE, true",
        "READ_ONLY_FILE_SYSTEM, true",
        "PERMISSION_DENIED, false",
        "ALREADY_EXISTS, false",
        "IO_ERROR, false"
    )
    fun `only the failures that every remaining path would hit as well stop a batch`(
        type: FileBrowserErrorType,
        stops: Boolean
    ) {
        // When / Then
        type.stopsABatch shouldBe stops
    }
}
