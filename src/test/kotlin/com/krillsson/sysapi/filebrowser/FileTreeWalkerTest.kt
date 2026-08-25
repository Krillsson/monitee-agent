package com.krillsson.sysapi.filebrowser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class FileTreeWalkerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
    }

    private fun walker(searchTimeoutSeconds: Long = 20) =
        fileBrowser(root, searchTimeoutSeconds = searchTimeoutSeconds).treeWalker

    private fun search(
        query: String,
        maxResults: Int = 500,
        maxDepth: Int = 16,
        includeDirectories: Boolean = true
    ) = walker().search(FileSearchInput(root.toString(), query, maxResults, maxDepth, includeDirectories))

    @Test
    fun `finds a name anywhere under the path, whatever its case`() {
        // Given
        root.resolve("Holiday.jpg").writeText("a")
        Files.createDirectory(root.resolve("nested")).resolve("holiday-2.jpg").writeText("b")
        root.resolve("other.txt").writeText("c")

        // When
        val result = search("holiday")

        // Then
        result.entries.map { it.name } shouldContainExactlyInAnyOrder listOf("Holiday.jpg", "holiday-2.jpg")
        result.truncated shouldBe false
    }

    @Test
    fun `leaves directories out when it is told to`() {
        // Given
        Files.createDirectory(root.resolve("holiday")).resolve("holiday.jpg").writeText("a")

        // When
        val result = search("holiday", includeDirectories = false)

        // Then
        result.entries.map { it.name } shouldContainExactlyInAnyOrder listOf("holiday.jpg")
    }

    @Test
    fun `stops at maxResults and says so`() {
        // Given
        (1..10).forEach { root.resolve("match-$it.txt").writeText("$it") }

        // When
        val result = search("match", maxResults = 3)

        // Then
        result.entries.size shouldBe 3
        result.truncated shouldBe true
    }

    @Test
    fun `stops at maxDepth and says so`() {
        // Given
        val deep = Files.createDirectories(root.resolve("one/two/three"))
        deep.resolve("match.txt").writeText("deep")

        // When
        val shallow = search("match", maxDepth = 2)
        val full = search("match", maxDepth = 8)

        // Then
        shallow.entries.shouldBeEmpty()
        shallow.truncated shouldBe true
        full.entries.map { it.name } shouldContainExactlyInAnyOrder listOf("match.txt")
        full.truncated shouldBe false
    }

    @Test
    fun `does not follow a symbolic link out of the root`() {
        // Given
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        outside.resolve("match.txt").writeText("outside")
        Files.createSymbolicLink(root.resolve("link"), outside)

        // When
        val result = search("match")

        // Then
        result.entries.shouldBeEmpty()
    }

    @Test
    fun `refuses a search without anything to look for`() {
        // When / Then
        shouldThrow<FileBrowserException> { search("   ") }
    }

    @Test
    fun `adds up a directory recursively without counting the directory itself`() {
        // Given
        root.resolve("a.txt").writeText("12345")
        val nested = Files.createDirectory(root.resolve("nested"))
        nested.resolve("b.txt").writeText("123")
        Files.createDirectory(nested.resolve("empty"))

        // When
        val size = walker().directorySize(root.toString(), 32)

        // Then
        size.totalBytes shouldBe 8
        size.fileCount shouldBe 2
        size.directoryCount shouldBe 2
        size.truncated shouldBe false
    }

    @Test
    fun `reports a size cut short by the depth limit as a lower bound`() {
        // Given
        root.resolve("a.txt").writeText("12345")
        Files.createDirectories(root.resolve("one/two")).resolve("b.txt").writeText("123")

        // When
        val size = walker().directorySize(root.toString(), 1)

        // Then
        size.totalBytes shouldBe 5
        size.truncated shouldBe true
    }

    private object AlwaysCancelledSink : FileOperationSink {
        override fun beginFile(path: String, sizeBytes: Long) = Unit
        override fun addBytes(bytes: Long) = Unit
        override fun fileDone() = Unit
        override fun succeeded(path: String) = Unit
        override fun failed(path: String, reason: String, type: FileBrowserErrorType) = Unit
        override fun isCancelled() = true
    }

    @Test
    fun `measures the files and bytes under a directory without counting the directory itself`() {
        // Given
        root.resolve("a.txt").writeText("12345")
        val nested = Files.createDirectory(root.resolve("nested"))
        nested.resolve("b.txt").writeText("123")

        // When
        val totals = walker().measure(listOf(root), countBytes = true, countDirectories = false, NoFileOperationSink)

        // Then
        totals?.files shouldBe 2
        totals?.bytes shouldBe 8
    }

    @Test
    fun `counts directories as entries too when asked to`() {
        // Given
        root.resolve("a.txt").writeText("12345")
        Files.createDirectory(root.resolve("nested"))

        // When
        val totals = walker().measure(listOf(root), countBytes = true, countDirectories = true, NoFileOperationSink)

        // Then
        totals?.files shouldBe 3
    }

    @Test
    fun `leaves the byte total null when it was not asked to count them`() {
        // Given
        root.resolve("a.txt").writeText("12345")

        // When
        val totals = walker().measure(listOf(root), countBytes = false, countDirectories = false, NoFileOperationSink)

        // Then
        totals?.files shouldBe 1
        totals?.bytes shouldBe null
    }

    @Test
    fun `gives up and returns null rather than a total cut short by the deadline`() {
        // Given
        root.resolve("a.txt").writeText("12345")

        // When
        val totals =
            walker(searchTimeoutSeconds = 0).measure(listOf(root), countBytes = true, countDirectories = false, NoFileOperationSink)

        // Then
        totals shouldBe null
    }

    @Test
    fun `aborts the walk instead of finishing it once the sink says the operation was cancelled`() {
        // Given
        root.resolve("a.txt").writeText("12345")

        // When / Then
        shouldThrow<FileOperationCancelledException> {
            walker().measure(listOf(root), countBytes = true, countDirectories = false, AlwaysCancelledSink)
        }
    }
}
