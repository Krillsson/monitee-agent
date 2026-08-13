package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FileOperationServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    private lateinit var browser: FileBrowser

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
        browser = fileBrowser(root)
    }

    private fun readOnly() = fileBrowser(root, access = FileBrowserAccess.READ)

    @Test
    fun `copies a file and reports it finished`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")

        // When
        val finished = browser.await(browser.operations.copy(file.toString(), root.resolve("copy.txt").toString(), false))

        // Then
        finished.state shouldBe FileOperationState.COMPLETED
        finished.type shouldBe FileOperationType.COPY
        finished.successes shouldContainExactly listOf(file.toString())
        finished.failures.shouldBeEmpty()
        finished.processedFiles shouldBe 1
        finished.processedBytes shouldBe 7
        finished.finishedAt.shouldNotBeNull()
        finished.currentPath shouldBe null
        root.resolve("copy.txt").readText() shouldBe "content"
    }

    @Test
    fun `knows the total up front for a file, and does not guess at one for a directory`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val directory = Files.createDirectory(root.resolve("directory"))
        directory.resolve("inner.txt").writeText("inner")

        // When
        val ofFile = browser.operations.copy(file.toString(), root.resolve("copy.txt").toString(), false)
        val ofDirectory = browser.operations.copy(directory.toString(), root.resolve("copied").toString(), false)

        // Then
        ofFile.totalFiles shouldBe 1
        ofFile.totalBytes shouldBe 7
        ofDirectory.totalFiles shouldBe null
        ofDirectory.totalBytes shouldBe null
        browser.await(ofFile).state shouldBe FileOperationState.COMPLETED
        browser.await(ofDirectory).state shouldBe FileOperationState.COMPLETED
    }

    @Test
    fun `never claims a byte total for a delete, because it does not move any`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")

        // When
        val started = browser.operations.delete(file.toString(), false)

        // Then
        started.totalFiles shouldBe 1
        started.totalBytes shouldBe null
        browser.await(started).processedFiles shouldBe 1
        Files.exists(file) shouldBe false
    }

    @Test
    fun `answers with the outcome of an operation that was already over before anyone asked`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")
        val started = browser.operations.copy(file.toString(), root.resolve("copy.txt").toString(), false)
        browser.await(started)

        // When
        val late = browser.await(started)

        // Then
        late.state shouldBe FileOperationState.COMPLETED
        browser.operations.find(started.id).shouldNotBeNull().state shouldBe FileOperationState.COMPLETED
    }

    @Test
    fun `copies a batch and reports the ones that failed without undoing the rest`() {
        // Given
        root.resolve("a.txt").writeText("a")
        root.resolve("b.txt").writeText("b")
        val destination = Files.createDirectory(root.resolve("destination"))

        // When
        val finished = browser.await(
            browser.operations.copyAll(
                listOf(root.resolve("a.txt").toString(), root.resolve("b.txt").toString()),
                destination.toString(),
                false
            )
        )

        // Then
        finished.state shouldBe FileOperationState.COMPLETED
        finished.successes shouldContainExactly listOf(
            root.resolve("a.txt").toString(),
            root.resolve("b.txt").toString()
        )
        destination.resolve("a.txt").readText() shouldBe "a"
        destination.resolve("b.txt").readText() shouldBe "b"
    }

    @Test
    fun `records a path that failed against that path and keeps going`() {
        // Given
        root.resolve("a.txt").writeText("a")
        val destination = Files.createDirectory(root.resolve("destination"))
        destination.resolve("b.txt").writeText("in the way")
        root.resolve("b.txt").writeText("b")

        // When
        val finished = browser.await(
            browser.operations.copyAll(
                listOf(root.resolve("b.txt").toString(), root.resolve("a.txt").toString()),
                destination.toString(),
                false
            )
        )

        // Then
        finished.state shouldBe FileOperationState.COMPLETED
        finished.failures.single().path shouldBe root.resolve("b.txt").toString()
        finished.successes shouldContainExactly listOf(root.resolve("a.txt").toString())
        destination.resolve("b.txt").readText() shouldBe "in the way"
        destination.resolve("a.txt").readText() shouldBe "a"
    }

    @Test
    fun `fails the operation when the work itself goes wrong part way through`() {
        // Given
        root.resolve("bundle.zip").writeText("this is not a zip at all")
        Files.createDirectory(root.resolve("unpacked"))

        // When
        val finished = browser.await(
            browser.operations.extractArchive(
                root.resolve("bundle.zip").toString(),
                root.resolve("unpacked").toString(),
                false
            )
        )

        // Then
        finished.state shouldBe FileOperationState.FAILED
        finished.reason.shouldNotBeNull()
        finished.successes.shouldBeEmpty()
        finished.finishedAt.shouldNotBeNull()
    }

    @Test
    fun `refuses a single path it cannot resolve without starting an operation`() {
        // When / Then
        shouldThrow<FileBrowserException> {
            browser.operations.copy(
                root.resolve("missing.txt").toString(),
                root.resolve("copy.txt").toString(),
                false
            )
        }
        browser.operations.all().shouldBeEmpty()
    }

    @Test
    fun `refuses an operation it can see is wrong before starting anything`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")

        // When / Then
        shouldThrow<FileBrowserException> {
            browser.operations.copy(file.toString(), tempDir.resolve("outside.txt").toString(), false)
        }
        shouldThrow<FileBrowserException> {
            readOnly().operations.copy(file.toString(), root.resolve("copy.txt").toString(), false)
        }
        shouldThrow<FileBrowserException> { browser.operations.copyAll(emptyList(), root.toString(), false) }
    }

    @Test
    fun `stops a batch that is cancelled and says that is what happened`() {
        // Given
        val destination = Files.createDirectory(root.resolve("destination"))
        val sources = (1..200).map { index ->
            root.resolve("file-$index.txt").also { it.writeText("x".repeat(2048)) }.toString()
        }

        // When
        val started = browser.operations.copyAll(sources, destination.toString(), false)
        browser.operations.cancel(started.id)
        val finished = browser.await(started)

        // Then
        finished.state shouldBe FileOperationState.CANCELLED
        finished.finishedAt.shouldNotBeNull()
    }

    @Test
    fun `lists the operations it is holding on to, newest first`() {
        // Given
        root.resolve("a.txt").writeText("a")
        val first = browser.await(browser.operations.copy(root.resolve("a.txt").toString(), root.resolve("b.txt").toString(), false))
        val second = browser.await(browser.operations.copy(root.resolve("a.txt").toString(), root.resolve("c.txt").toString(), false))

        // When
        val all = browser.operations.all()

        // Then
        all.map { it.id } shouldContainExactly listOf(second.id, first.id)
    }

    @Test
    fun `does not hand out events for an operation it has never heard of`() {
        // When / Then
        shouldThrow<FileBrowserException> { browser.operations.events("not-an-operation") }
        browser.operations.find("not-an-operation") shouldBe null
    }

    @Test
    fun `runs an archive and a trash empty as operations too`() {
        // Given
        val directory = Files.createDirectory(root.resolve("photos"))
        directory.resolve("one.txt").writeText("one")
        Files.createDirectory(root.resolve("unpacked"))

        // When
        val packed = browser.await(
            browser.operations.createArchive(listOf(directory.toString()), root.resolve("bundle.zip").toString(), false)
        )
        val unpacked = browser.await(
            browser.operations.extractArchive(
                root.resolve("bundle.zip").toString(),
                root.resolve("unpacked").toString(),
                false
            )
        )
        browser.trash.trash(directory.toString())
        val emptied = browser.await(browser.operations.emptyTrash(null))

        // Then
        packed.state shouldBe FileOperationState.COMPLETED
        packed.type shouldBe FileOperationType.CREATE_ARCHIVE
        unpacked.state shouldBe FileOperationState.COMPLETED
        unpacked.processedFiles shouldBe 2
        root.resolve("unpacked/photos/one.txt").readText() shouldBe "one"
        emptied.state shouldBe FileOperationState.COMPLETED
        browser.trash.list().shouldBeEmpty()
    }

    @Test
    fun `counts a rename as having moved the file, so it does not sit at nothing and then end`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")

        // When
        val finished = browser.await(
            browser.operations.move(file.toString(), root.resolve("renamed.txt").toString(), false)
        )

        // Then
        finished.state shouldBe FileOperationState.COMPLETED
        finished.processedFiles shouldBe 1
        finished.processedBytes shouldBe 7
        finished.totalBytes shouldBe 7
        root.resolve("renamed.txt").readText() shouldBe "content"
    }
}
