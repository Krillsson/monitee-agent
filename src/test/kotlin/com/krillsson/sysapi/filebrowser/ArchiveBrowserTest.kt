package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeText

class ArchiveBrowserTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
    }

    private fun browser(access: FileBrowserAccess = FileBrowserAccess.READ_WRITE) =
        fileBrowser(root, access = access).archiveBrowser

    private fun zip(name: String, vararg entries: Pair<String, String?>): Path {
        val archive = root.resolve(name)
        ZipOutputStream(Files.newOutputStream(archive)).use { output ->
            entries.forEach { (path, contents) ->
                output.putNextEntry(ZipEntry(path))
                contents?.let { output.write(it.toByteArray(StandardCharsets.UTF_8)) }
                output.closeEntry()
            }
        }
        return archive
    }

    private val bundle
        get() = zip(
            "bundle.zip",
            "top.txt" to "top",
            "photos/" to null,
            "photos/one.jpg" to "one",
            "photos/two.jpg" to "two",
            "photos/raw/three.dng" to "three"
        )

    @Test
    fun `lists the top of an archive, directories first`() {
        // Given
        val archive = bundle

        // When
        val listing = browser().list(archive.toString(), null)

        // Then
        listing.entryPath shouldBe ""
        listing.entries.map { it.name } shouldContainExactly listOf("photos", "top.txt")
        listing.entries.first().type shouldBe FileEntryType.DIRECTORY
        listing.truncated shouldBe false
    }

    @Test
    fun `goes into a directory inside the archive`() {
        // Given
        val archive = bundle

        // When
        val listing = browser().list(archive.toString(), "photos")

        // Then
        listing.entryPath shouldBe "photos"
        listing.entries.map { it.name } shouldContainExactly listOf("raw", "one.jpg", "two.jpg")
        listing.entries.map { it.entryPath } shouldContainExactly
                listOf("photos/raw", "photos/one.jpg", "photos/two.jpg")
    }

    @Test
    fun `makes up the directories an archive only implies by the names in it`() {
        // Given
        val archive = zip("flat.zip", "a/b/c/deep.txt" to "deep")

        // When
        val top = browser().list(archive.toString(), null)
        val middle = browser().list(archive.toString(), "a/b")

        // Then
        top.entries.map { it.name } shouldContainExactly listOf("a")
        top.entries.single().type shouldBe FileEntryType.DIRECTORY
        middle.entries.map { it.name } shouldContainExactly listOf("c")
    }

    @Test
    fun `reports the size and the type of what it finds`() {
        // Given
        val archive = bundle

        // When
        val entry = browser().list(archive.toString(), "photos").entries.single { it.name == "one.jpg" }

        // Then
        entry.type shouldBe FileEntryType.FILE
        entry.sizeBytes shouldBe 3
        entry.mimeType shouldBe "image/jpeg"
        entry.iconId shouldBe "jpg"
    }

    @Test
    fun `reads one entry out without unpacking the rest`() {
        // Given
        val archive = bundle

        // When
        val contents = browser().readEntry(archive.toString(), "photos/raw/three.dng") {
            it.readBytes().toString(StandardCharsets.UTF_8)
        }

        // Then
        contents shouldBe "three"
        Files.list(root).use { it.toList() }.map { it.fileName.toString() } shouldContainExactly listOf("bundle.zip")
    }

    @Test
    fun `refuses to read a directory, and something that is not in the archive`() {
        // Given
        val archive = bundle
        val browser = browser()

        // When / Then
        shouldThrow<FileBrowserException> { browser.readEntry(archive.toString(), "photos") {} }
        shouldThrow<FileBrowserException> { browser.readEntry(archive.toString(), "nothing.txt") {} }
        shouldThrow<FileBrowserException> { browser.list(archive.toString(), "not-a-directory") }
    }

    @Test
    fun `refuses an entry path that tries to climb out`() {
        // Given
        val archive = bundle

        // When / Then
        shouldThrow<FileBrowserException> { browser().list(archive.toString(), "../elsewhere") }
    }

    @Test
    fun `only looks into a zip`() {
        // Given
        val archive = root.resolve("bundle.tar.gz")
        archive.writeText("not really a tarball")

        // When
        val failure = shouldThrow<FileBrowserException> { browser().list(archive.toString(), null) }

        // Then
        failure.message.orEmpty() shouldContain "Only a zip"
    }

    @Test
    fun `looks inside in read mode, because looking is reading`() {
        // Given
        val archive = bundle

        // When
        val listing = browser(access = FileBrowserAccess.READ).list(archive.toString(), null)

        // Then
        listing.entries.map { it.name } shouldContainExactly listOf("photos", "top.txt")
    }

    @Test
    fun `says a zip can be browsed and a tarball cannot`() {
        // Given
        val factory = fileBrowser(root).entryFactory
        val archive = bundle
        val tarball = root.resolve("bundle.tar.gz")
        tarball.writeText("not really a tarball")

        // When / Then
        factory.entryOf(archive).browsableAsArchive shouldBe true
        factory.entryOf(archive).isArchive shouldBe true
        factory.entryOf(tarball).browsableAsArchive shouldBe false
        factory.entryOf(tarball).isArchive shouldBe true
    }

    @Test
    fun `refuses a file that only has the name of a zip`() {
        // Given
        val archive = root.resolve("bundle.zip")
        archive.writeText("not a zip at all")

        // When / Then
        shouldThrow<FileBrowserException> { browser().list(archive.toString(), null) }
    }

    @Test
    fun `keeps the timestamp of a directory the archive does name`() {
        // Given
        val archive = bundle

        // When
        val named = browser().list(archive.toString(), null).entries.single { it.name == "photos" }
        val impliedOnly = browser().list(zip("flat.zip", "a/deep.txt" to "deep").toString(), null).entries.single()

        // Then
        named.type shouldBe FileEntryType.DIRECTORY
        named.updatedAt shouldNotBe null
        impliedOnly.type shouldBe FileEntryType.DIRECTORY
        impliedOnly.updatedAt shouldBe null
    }
}
