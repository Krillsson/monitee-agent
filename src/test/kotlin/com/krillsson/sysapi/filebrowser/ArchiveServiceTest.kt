package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ArchiveServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    private lateinit var destination: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
        destination = Files.createDirectory(root.resolve("unpacked"))
    }

    private fun archives(
        access: FileBrowserAccess = FileBrowserAccess.READ_WRITE,
        maxArchiveEntries: Int = 100_000,
        maxArchiveBytes: Long = 21_474_836_480
    ) = fileBrowser(
        root,
        access = access,
        maxArchiveEntries = maxArchiveEntries,
        maxArchiveBytes = maxArchiveBytes
    ).archives

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

    private fun tar(name: String, gzip: Boolean, vararg entries: Pair<String, String?>): Path {
        val archive = root.resolve(name)
        val bytes = ByteArrayOutputStream()
        entries.forEach { (path, contents) ->
            val body = contents?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
            if (path.length > 100) {
                val longName = path.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0)
                bytes.write(tarHeader("././@LongLink", longName.size, "L"))
                bytes.write(longName)
                bytes.write(ByteArray((512 - longName.size % 512) % 512))
            }
            bytes.write(tarHeader(path, body.size, if (contents == null) "5" else "0"))
            if (contents != null) {
                bytes.write(body)
                bytes.write(ByteArray((512 - body.size % 512) % 512))
            }
        }
        bytes.write(ByteArray(1024))
        Files.newOutputStream(archive).use { file ->
            val sink: OutputStream = if (gzip) GZIPOutputStream(file) else file
            sink.write(bytes.toByteArray())
            sink.flush()
            if (gzip) {
                sink.close()
            }
        }
        return archive
    }

    private fun tarHeader(path: String, size: Int, type: String): ByteArray {
        val header = ByteArray(512)
        fun put(offset: Int, value: String) =
            value.toByteArray(StandardCharsets.UTF_8).copyInto(header, offset)
        put(0, path.take(100))
        put(100, "0000644")
        put(108, "0000000")
        put(116, "0000000")
        put(124, String.format("%011o", size))
        put(136, String.format("%011o", 0))
        put(156, type)
        put(257, "ustar")
        put(263, "00")
        put(148, "        ")
        val checksum = header.sumOf { it.toInt() and 0xff }
        put(148, String.format("%06o", checksum))
        return header
    }

    @Test
    fun `unpacks a zip into a directory`() {
        // Given
        val archive = zip("bundle.zip", "top.txt" to "top", "nested/deep.txt" to "deep")

        // When
        val extraction = archives().extract(archive.toString(), destination.toString(), overwrite = false)

        // Then
        destination.resolve("top.txt").readText() shouldBe "top"
        destination.resolve("nested/deep.txt").readText() shouldBe "deep"
        extraction.entryCount shouldBe 2
        extraction.totalBytes shouldBe 7
    }

    @Test
    fun `unpacks a tar, with its directories and names too long for a header`() {
        // Given
        val longName = "a".repeat(120)
        val archive = tar(
            "bundle.tar",
            gzip = false,
            "nested/" to null,
            "nested/$longName.txt" to "deep",
            "top.txt" to "top"
        )

        // When
        archives().extract(archive.toString(), destination.toString(), overwrite = false)

        // Then
        destination.resolve("nested/$longName.txt").readText() shouldBe "deep"
        destination.resolve("top.txt").readText() shouldBe "top"
    }

    @Test
    fun `unpacks a gzipped tar`() {
        // Given
        val archive = tar("bundle.tar.gz", gzip = true, "top.txt" to "top")

        // When
        archives().extract(archive.toString(), destination.toString(), overwrite = false)

        // Then
        destination.resolve("top.txt").readText() shouldBe "top"
    }

    @Test
    fun `unpacks a plain gzipped file under the name without the suffix`() {
        // Given
        val archive = root.resolve("syslog.log.gz")
        GZIPOutputStream(Files.newOutputStream(archive)).use { it.write("logged".toByteArray()) }

        // When
        archives().extract(archive.toString(), destination.toString(), overwrite = false)

        // Then
        destination.resolve("syslog.log").readText() shouldBe "logged"
    }

    @Test
    fun `refuses an entry that points out of the destination`() {
        // Given
        val archive = zip("evil.zip", "../escaped.txt" to "escaped")

        // When
        val failure = shouldThrow<FileBrowserException> {
            archives().extract(archive.toString(), destination.toString(), overwrite = false)
        }

        // Then
        failure.message.orEmpty() shouldContain "points outside"
        Files.exists(root.resolve("escaped.txt")) shouldBe false
    }

    @Test
    fun `refuses an entry with an absolute path`() {
        // Given
        val archive = zip("evil.zip", "/etc/passwd" to "escaped")

        // When / Then
        shouldThrow<FileBrowserException> {
            archives().extract(archive.toString(), destination.toString(), overwrite = false)
        }
    }

    @Test
    fun `refuses to write through a symbolic link left in the destination`() {
        // Given
        val outside = Files.createDirectory(tempDir.resolve("outside"))
        Files.createSymbolicLink(destination.resolve("link"), outside)
        val archive = zip("evil.zip", "link/escaped.txt" to "escaped")

        // When / Then
        shouldThrow<FileBrowserException> {
            archives().extract(archive.toString(), destination.toString(), overwrite = false)
        }
        Files.exists(outside.resolve("escaped.txt")) shouldBe false
    }

    @Test
    fun `refuses to replace a file that is already there unless it is told to`() {
        // Given
        destination.resolve("top.txt").writeText("mine")
        val archive = zip("bundle.zip", "top.txt" to "theirs")

        // When
        shouldThrow<FileAlreadyThereException> {
            archives().extract(archive.toString(), destination.toString(), overwrite = false)
        }

        // Then
        destination.resolve("top.txt").readText() shouldBe "mine"
        archives().extract(archive.toString(), destination.toString(), overwrite = true)
        destination.resolve("top.txt").readText() shouldBe "theirs"
    }

    @Test
    fun `gives up on an archive that unpacks to more than it is allowed to`() {
        // Given
        val archive = zip("bomb.zip", "big.txt" to "x".repeat(2048))

        // When / Then
        shouldThrow<FileBrowserException> {
            archives(maxArchiveBytes = 1024).extract(archive.toString(), destination.toString(), overwrite = false)
        }
    }

    @Test
    fun `gives up on an archive with more entries than it is allowed to hold`() {
        // Given
        val archive = zip("many.zip", "a.txt" to "a", "b.txt" to "b", "c.txt" to "c")

        // When / Then
        shouldThrow<FileBrowserException> {
            archives(maxArchiveEntries = 2).extract(archive.toString(), destination.toString(), overwrite = false)
        }
    }

    @Test
    fun `packs files and directories into a zip that it can read back`() {
        // Given
        val directory = Files.createDirectory(root.resolve("photos"))
        directory.resolve("one.jpg").writeText("one")
        Files.createDirectory(directory.resolve("nested")).resolve("two.jpg").writeText("two")
        root.resolve("notes.txt").writeText("notes")
        val service = archives()

        // When
        val entry = service.create(
            listOf(directory.toString(), root.resolve("notes.txt").toString()),
            root.resolve("bundle.zip").toString(),
            overwrite = false
        )
        service.extract(entry.path, destination.toString(), overwrite = false)

        // Then
        entry.isArchive shouldBe true
        destination.resolve("photos/one.jpg").readText() shouldBe "one"
        destination.resolve("photos/nested/two.jpg").readText() shouldBe "two"
        destination.resolve("notes.txt").readText() shouldBe "notes"
    }

    @Test
    fun `refuses to write an archive into what it is archiving`() {
        // Given
        val directory = Files.createDirectory(root.resolve("photos"))
        directory.resolve("one.jpg").writeText("one")

        // When / Then
        shouldThrow<FileBrowserException> {
            archives().create(
                listOf(directory.toString()),
                directory.resolve("bundle.zip").toString(),
                overwrite = false
            )
        }
    }

    @Test
    fun `only writes zip archives`() {
        // Given
        root.resolve("notes.txt").writeText("notes")

        // When / Then
        shouldThrow<FileBrowserException> {
            archives().create(
                listOf(root.resolve("notes.txt").toString()),
                root.resolve("bundle.tar.gz").toString(),
                overwrite = false
            )
        }
    }

    @Test
    fun `refuses every archive operation in read mode`() {
        // Given
        val archive = zip("bundle.zip", "top.txt" to "top")
        val service = archives(access = FileBrowserAccess.READ)

        // When / Then
        shouldThrow<FileBrowserException> {
            service.extract(archive.toString(), destination.toString(), overwrite = false)
        }
        shouldThrow<FileBrowserException> {
            service.create(listOf(archive.toString()), root.resolve("copy.zip").toString(), overwrite = false)
        }
    }

    @Test
    fun `lists what a packed archive holds`() {
        // Given
        val directory = Files.createDirectory(root.resolve("photos"))
        directory.resolve("one.jpg").writeText("one")

        // When
        val entry = archives().create(
            listOf(directory.toString()),
            root.resolve("bundle.zip").toString(),
            overwrite = false
        )

        // Then
        namesIn(Path.of(entry.path)) shouldContainExactlyInAnyOrder listOf("photos/", "photos/one.jpg")
    }

    @Test
    fun `unpacks only the entries it was asked for, folders and all`() {
        // Given
        val archive = zip(
            "bundle.zip",
            "top.txt" to "top",
            "photos/one.jpg" to "one",
            "photos/raw/two.dng" to "two",
            "notes/three.txt" to "three"
        )

        // When
        val extraction = archives().extract(
            archive.toString(),
            destination.toString(),
            overwrite = false,
            entries = listOf("photos", "top.txt")
        )

        // Then
        extraction.entryCount shouldBe 3
        destination.resolve("top.txt").readText() shouldBe "top"
        destination.resolve("photos/one.jpg").readText() shouldBe "one"
        destination.resolve("photos/raw/two.dng").readText() shouldBe "two"
        Files.exists(destination.resolve("notes")) shouldBe false
    }

    @Test
    fun `refuses an extraction that asks for entries the archive does not hold`() {
        // Given
        val archive = zip("bundle.zip", "top.txt" to "top")

        // When / Then
        shouldThrow<FileBrowserException> {
            archives().extract(
                archive.toString(),
                destination.toString(),
                overwrite = false,
                entries = listOf("nothing.txt")
            )
        }
    }

    private fun namesIn(archive: Path): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(Files.newInputStream(archive)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                names.add(entry.name)
                input.closeEntry()
            }
        }
        return names
    }
}