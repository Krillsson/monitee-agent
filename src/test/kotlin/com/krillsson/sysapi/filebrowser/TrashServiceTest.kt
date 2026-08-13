package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
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

class TrashServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
    }

    private fun browser(access: FileBrowserAccess = FileBrowserAccess.READ_WRITE, trash: Boolean = true) =
        fileBrowser(root, access = access, trash = trash)

    @Test
    fun `takes a file out of its directory and remembers where it came from`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val browser = browser()

        // When
        val trashed = browser.trash.trash(file.toString())

        // Then
        Files.exists(file) shouldBe false
        trashed.originalPath shouldBe file.toString()
        trashed.entry.name shouldBe "notes.txt"
        browser.trash.list().map { it.id } shouldContainExactly listOf(trashed.id)
    }

    @Test
    fun `puts a file back where it came from`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val browser = browser()
        val trashed = browser.trash.trash(file.toString())

        // When
        val restored = browser.trash.restore(trashed.id, overwrite = false)

        // Then
        restored.path shouldBe file.toString()
        file.readText() shouldBe "content"
        browser.trash.list().shouldBeEmpty()
    }

    @Test
    fun `refuses to restore over something that has taken the original path back`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val browser = browser()
        val trashed = browser.trash.trash(file.toString())
        file.writeText("something else")

        // When
        shouldThrow<FileAlreadyThereException> { browser.trash.restore(trashed.id, overwrite = false) }

        // Then
        file.readText() shouldBe "something else"
        browser.trash.restore(trashed.id, overwrite = true)
        file.readText() shouldBe "content"
    }

    @Test
    fun `empties one entry, and then everything`() {
        // Given
        root.resolve("a.txt").writeText("a")
        root.resolve("b.txt").writeText("b")
        val browser = browser()
        val first = browser.trash.trash(root.resolve("a.txt").toString())
        browser.trash.trash(root.resolve("b.txt").toString())

        // When
        browser.trash.empty(first.id) shouldBe 1

        // Then
        browser.trash.list().size shouldBe 1
        browser.trash.empty(null) shouldBe 1
        browser.trash.list().shouldBeEmpty()
    }

    @Test
    fun `keeps the trash out of listings and searches`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val browser = browser()
        browser.trash.trash(file.toString())

        // When
        val listing = browser.manager.listDirectory(root.toString())
        val found = browser.treeWalker.search(FileSearchInput(root.toString(), "notes"))

        // Then
        listing.entries.shouldBeEmpty()
        found.entries.shouldBeEmpty()
    }

    @Test
    fun `refuses to trash a root, and to trash what is already in the trash`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val browser = browser()
        val trashed = browser.trash.trash(file.toString())

        // When / Then
        shouldThrow<FileBrowserException> { browser.trash.trash(root.toString()) }
        shouldThrow<FileBrowserException> { browser.trash.trash(trashed.entry.path) }
    }

    @Test
    fun `answers with an error when it is switched off`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val browser = browser(trash = false)

        // When / Then
        browser.trash.enabled shouldBe false
        shouldThrow<FileBrowserException> { browser.trash.trash(file.toString()) }
    }

    @Test
    fun `refuses to trash anything in read mode`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val browser = browser(access = FileBrowserAccess.READ)

        // When / Then
        shouldThrow<FileBrowserException> { browser.trash.trash(file.toString()) }
        file.readText() shouldBe "content"
    }
}
