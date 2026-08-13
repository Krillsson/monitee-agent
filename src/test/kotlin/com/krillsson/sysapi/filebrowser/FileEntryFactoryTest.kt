package com.krillsson.sysapi.filebrowser

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.writeText

class FileEntryFactoryTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
    }

    private fun factory() = fileBrowser(root).entryFactory

    @Test
    fun `reads the posix metadata the properties sheet shows`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"))

        // When
        val entry = factory().entryOf(file)

        // Then
        entry.permissions shouldBe "rw-r-----"
        entry.owner.shouldNotBeNull()
        entry.group.shouldNotBeNull()
        entry.accessedAt.shouldNotBeNull()
    }

    @Test
    fun `names what a symbolic link points at`() {
        // Given
        val link = root.resolve("link")
        Files.createSymbolicLink(link, tempDir.resolve("elsewhere"))

        // When
        val entry = factory().entryOf(link)

        // Then
        entry.type shouldBe FileEntryType.SYMLINK
        entry.linkTarget shouldBe tempDir.resolve("elsewhere").toString()
    }

    @Test
    fun `leaves linkTarget out for everything that is not a link`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")

        // When / Then
        factory().entryOf(file).linkTarget shouldBe null
    }

    @Test
    fun `calls a name with a leading dot hidden`() {
        // Given
        val hidden = root.resolve(".env")
        hidden.writeText("secret")
        val plain = root.resolve("env")
        plain.writeText("plain")

        // When / Then
        factory().entryOf(hidden).isHidden shouldBe true
        factory().entryOf(plain).isHidden shouldBe false
    }

    @Test
    fun `marks the archives that can be extracted and the images that can be scaled`() {
        // Given
        val archive = root.resolve("bundle.tar.gz")
        archive.writeText("not really a tarball")
        val image = root.resolve("photo.jpg")
        image.writeText("not really a photo")
        val other = root.resolve("notes.txt")
        other.writeText("notes")
        val factory = factory()

        // When / Then
        factory.entryOf(archive).isArchive shouldBe true
        factory.entryOf(archive).hasThumbnail shouldBe false
        factory.entryOf(image).hasThumbnail shouldBe true
        factory.entryOf(image).isArchive shouldBe false
        factory.entryOf(other).isArchive shouldBe false
        factory.entryOf(other).hasThumbnail shouldBe false
    }

    @Test
    fun `does not offer a thumbnail for an image larger than the limit`() {
        // Given
        val image = root.resolve("photo.jpg")
        image.writeText("x".repeat(64))

        // When / Then
        fileBrowser(root, maxThumbnailSourceBytes = 16).entryFactory.entryOf(image).hasThumbnail shouldBe false
        fileBrowser(root, thumbnails = false).entryFactory.entryOf(image).hasThumbnail shouldBe false
    }

    @Test
    fun `sizes a directory as zero and gives it the folder icon`() {
        // Given
        val directory = Files.createDirectory(root.resolve("nested"))
        directory.resolve("file.txt").writeText("content")

        // When
        val entry = factory().entryOf(directory)

        // Then
        entry.type shouldBe FileEntryType.DIRECTORY
        entry.sizeBytes shouldBe 0
        entry.iconId shouldBe FileTypeRegistry.FOLDER_ICON
        entry.mimeType shouldBe null
    }
}
