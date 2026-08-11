package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import com.krillsson.sysapi.config.FileBrowserConfiguration
import io.kotest.assertions.throwables.shouldThrow
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
    ): FileBrowserManager {
        val configuration = FileBrowserConfiguration(
            enabled = true,
            access = access,
            roots = listOf(root.toString()),
            maxEditableBytes = maxEditableBytes
        )
        return FileBrowserManager(configuration, FileBrowserSandbox(configuration), FileTypeRegistry())
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
