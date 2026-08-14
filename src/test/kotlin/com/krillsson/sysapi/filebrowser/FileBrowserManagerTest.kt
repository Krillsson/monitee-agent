package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import com.krillsson.sysapi.config.FileBrowserConfiguration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FileBrowserManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
    }

    private fun manager(
        access: FileBrowserAccess = FileBrowserAccess.READ_WRITE,
        maxEditableBytes: Long = 1024
    ): FileBrowserManager = fileBrowser(root, access = access, maxEditableBytes = maxEditableBytes).manager

    @Test
    fun `mirrors the configured limits, so a client can learn a ceiling before hitting it`() {
        // Given
        val configuration = FileBrowserConfiguration(
            enabled = true,
            roots = listOf(root.toString()),
            maxEditableBytes = 111,
            maxUploadBytes = 222,
            maxLogViewBytes = 333,
            searchTimeoutSeconds = 4,
            thumbnails = false,
            maxThumbnailSourceBytes = 555,
            maxArchiveEntries = 6,
            maxArchiveBytes = 777,
            fileOperationRetentionMinutes = 8
        )
        val manager = FileBrowser(configuration, tempDir.resolve("cache")).manager

        // When
        val limits = manager.limits

        // Then
        limits shouldBe FileBrowserLimits(
            maxEditableBytes = 111,
            maxUploadBytes = 222,
            maxLogViewBytes = 333,
            searchTimeoutSeconds = 4,
            thumbnails = false,
            maxThumbnailSourceBytes = 555,
            maxArchiveEntries = 6,
            maxArchiveBytes = 777,
            fileOperationRetentionMinutes = 8
        )
    }

    @Test
    fun `lists directories before files, both by name`() {
        // Given
        Files.createFile(root.resolve("b.txt"))
        Files.createFile(root.resolve("a.txt"))
        Files.createDirectory(root.resolve("zzz"))
        val manager = manager()

        // When
        val listing = manager.listDirectory(root.toString())

        // Then
        listing.entries.map { it.name } shouldContainExactly listOf("zzz", "a.txt", "b.txt")
        listing.truncated shouldBe false
    }

    @Test
    fun `lists a symbolic link without following it`() {
        // Given
        Files.createSymbolicLink(root.resolve("link"), tempDir.resolve("elsewhere"))
        val manager = manager()

        // When
        val listing = manager.listDirectory(root.toString())

        // Then
        listing.entries.single().type shouldBe FileEntryType.SYMLINK
    }

    @Test
    fun `opens a text file`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("hello\nworld")
        val manager = manager()

        // When
        val content = manager.openTextFile(file.toString())

        // Then
        content.contents shouldBe "hello\nworld"
        content.entry.editable shouldBe true
    }

    @Test
    fun `refuses to open a file that is not valid text`() {
        // Given
        val file = root.resolve("photo.txt")
        Files.write(file, byteArrayOf(0x41, 0x00, 0x42))
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> { manager.openTextFile(file.toString()) }
    }

    @Test
    fun `refuses to open a file larger than the editing limit`() {
        // Given
        val file = root.resolve("big.txt")
        file.writeText("x".repeat(2048))
        val manager = manager(maxEditableBytes = 1024)

        // When / Then
        shouldThrow<FileBrowserException> { manager.openTextFile(file.toString()) }
        manager.entryOf(file).editable shouldBe false
    }

    @Test
    fun `saves a text file and leaves no temporary file behind`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("before")
        val manager = manager()

        // When
        manager.saveTextFile(file.toString(), "after")

        // Then
        file.readText() shouldBe "after"
        Files.list(root).use { it.toList() }.map { it.fileName.toString() } shouldContainExactly listOf("notes.txt")
    }

    @Test
    fun `leaves the original in place when a save fails`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("before")
        val manager = manager(maxEditableBytes = 8)

        // When
        shouldThrow<FileBrowserException> { manager.saveTextFile(file.toString(), "far too much content") }

        // Then
        file.readText() shouldBe "before"
        Files.list(root).use { it.toList() }.map { it.fileName.toString() } shouldContainExactly listOf("notes.txt")
    }

    @Test
    fun `copies a directory with everything in it`() {
        // Given
        val source = Files.createDirectory(root.resolve("source"))
        Files.createDirectory(source.resolve("nested")).resolve("deep.txt").writeText("deep")
        val manager = manager()

        // When
        manager.copy(source.toString(), root.resolve("copy").toString())

        // Then
        root.resolve("copy/nested/deep.txt").readText() shouldBe "deep"
        source.resolve("nested/deep.txt").readText() shouldBe "deep"
    }

    @Test
    fun `moves a file, which is also how it is renamed`() {
        // Given
        val file = root.resolve("before.txt")
        file.writeText("content")
        val manager = manager()

        // When
        manager.move(file.toString(), root.resolve("after.txt").toString())

        // Then
        Files.exists(file) shouldBe false
        root.resolve("after.txt").readText() shouldBe "content"
    }

    @Test
    fun `refuses a destination that already exists`() {
        // Given
        val file = root.resolve("a.txt")
        file.writeText("a")
        root.resolve("b.txt").writeText("b")
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> { manager.move(file.toString(), root.resolve("b.txt").toString()) }
        shouldThrow<FileBrowserException> { manager.copy(file.toString(), root.resolve("b.txt").toString()) }
    }

    @Test
    fun `replaces the destination when it is told to overwrite`() {
        // Given
        val file = root.resolve("a.txt")
        file.writeText("a")
        val other = root.resolve("b.txt")
        other.writeText("b")
        val manager = manager()

        // When
        manager.copy(file.toString(), other.toString(), overwrite = true)

        // Then
        other.readText() shouldBe "a"
        file.readText() shouldBe "a"
    }

    @Test
    fun `leaves the destination alone when an overwriting copy fails`() {
        // Given
        val destination = root.resolve("b.txt")
        destination.writeText("original")
        val manager = manager()

        // When
        shouldThrow<Exception> {
            manager.copy(root.resolve("missing.txt").toString(), destination.toString(), overwrite = true)
        }

        // Then
        destination.readText() shouldBe "original"
        Files.list(root).use { it.toList() }.map { it.fileName.toString() } shouldContainExactly listOf("b.txt")
    }

    @Test
    fun `moves onto an existing destination when it is told to overwrite`() {
        // Given
        val file = root.resolve("a.txt")
        file.writeText("a")
        val other = root.resolve("b.txt")
        other.writeText("b")
        val manager = manager()

        // When
        manager.move(file.toString(), other.toString(), overwrite = true)

        // Then
        other.readText() shouldBe "a"
        Files.exists(file) shouldBe false
    }

    @Test
    fun `refuses to overwrite a directory with a file, and the other way round`() {
        // Given
        val file = root.resolve("a.txt")
        file.writeText("a")
        val directory = Files.createDirectory(root.resolve("b"))
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> { manager.copy(file.toString(), directory.toString(), overwrite = true) }
        shouldThrow<FileBrowserException> { manager.move(directory.toString(), file.toString(), overwrite = true) }
    }

    @Test
    fun `refuses to copy something onto itself`() {
        // Given
        val file = root.resolve("a.txt")
        file.writeText("a")
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> { manager.copy(file.toString(), file.toString(), overwrite = true) }
        file.readText() shouldBe "a"
    }

    @Test
    fun `merges a directory copied over an existing one`() {
        // Given
        val source = Files.createDirectory(root.resolve("source"))
        source.resolve("shared.txt").writeText("new")
        source.resolve("only-source.txt").writeText("source")
        val destination = Files.createDirectory(root.resolve("destination"))
        destination.resolve("shared.txt").writeText("old")
        destination.resolve("only-destination.txt").writeText("destination")
        val manager = manager()

        // When
        manager.copy(source.toString(), destination.toString(), overwrite = true)

        // Then
        destination.resolve("shared.txt").readText() shouldBe "new"
        destination.resolve("only-source.txt").readText() shouldBe "source"
        destination.resolve("only-destination.txt").readText() shouldBe "destination"
    }

    @Test
    fun `pages a directory in the same order as it lists it`() {
        // Given
        (1..9).forEach { root.resolve("file-$it.txt").writeText("$it") }
        Files.createDirectory(root.resolve("directory"))
        val manager = manager()
        val listed = manager.listDirectory(root.toString()).entries.map { it.name }

        // When
        val paged = mutableListOf<String>()
        var after: String? = null
        do {
            val page = manager.listDirectoryPage(root.toString(), after, 4)
            paged += page.edges.map { it.node.name }
            after = page.pageInfo.endCursor
            page.totalCount shouldBe 10
        } while (page.pageInfo.hasNextPage)

        // Then
        paged shouldContainExactly listed
    }

    @Test
    fun `refuses a cursor it did not hand out`() {
        // Given
        root.resolve("a.txt").writeText("a")
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> { manager.listDirectoryPage(root.toString(), "!! not a cursor !!", 10) }
    }

    @Test
    fun `refuses to delete a directory that is not empty unless it is told to`() {
        // Given
        val directory = Files.createDirectory(root.resolve("full"))
        directory.resolve("file.txt").writeText("content")
        val manager = manager()

        // When
        shouldThrow<FileBrowserException> { manager.delete(directory.toString(), recursive = false) }

        // Then
        manager.delete(directory.toString(), recursive = true)
        Files.exists(directory) shouldBe false
    }

    @Test
    fun `refuses to delete a root`() {
        // Given
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> { manager.delete(root.toString(), recursive = true) }
    }

    @Test
    fun `creates a directory`() {
        // Given
        val manager = manager()

        // When
        val entry = manager.createDirectory(root.resolve("new").toString())

        // Then
        entry.type shouldBe FileEntryType.DIRECTORY
        entry.iconId shouldBe FileTypeRegistry.FOLDER_ICON
        Files.isDirectory(root.resolve("new")) shouldBe true
    }

    @Test
    fun `refuses every mutating operation in read mode`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val directory = Files.createDirectory(root.resolve("directory"))
        val manager = manager(access = FileBrowserAccess.READ)

        // When / Then
        shouldThrow<FileBrowserException> { manager.saveTextFile(file.toString(), "changed") }
        shouldThrow<FileBrowserException> { manager.copy(file.toString(), root.resolve("copy.txt").toString()) }
        shouldThrow<FileBrowserException> { manager.move(file.toString(), root.resolve("moved.txt").toString()) }
        shouldThrow<FileBrowserException> { manager.delete(file.toString(), recursive = false) }
        shouldThrow<FileBrowserException> { manager.createDirectory(root.resolve("new").toString()) }
        file.readText() shouldBe "content"
        Files.isDirectory(directory) shouldBe true
    }

    @Test
    fun `reading still works in read mode`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val manager = manager(access = FileBrowserAccess.READ)

        // When
        val content = manager.openTextFile(file.toString())

        // Then
        content.contents shouldBe "content"
        manager.listDirectory(root.toString()).entries.single().name shouldBe "notes.txt"
    }
}
