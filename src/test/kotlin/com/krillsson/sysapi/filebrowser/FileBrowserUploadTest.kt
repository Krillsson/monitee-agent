package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FileBrowserUploadTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    private val twoHoursAgo: FileTime = FileTime.from(Instant.now().minus(Duration.ofHours(2)))

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
    }

    private fun manager(
        access: FileBrowserAccess = FileBrowserAccess.READ_WRITE,
        maxUploadBytes: Long = 0
    ): FileBrowserManager = fileBrowser(root, access = access, maxUploadBytes = maxUploadBytes).manager

    private fun namesIn(directory: Path) = Files.list(directory).use { it.toList() }.map { it.name }.sorted()

    @Test
    fun `stores the bytes it is given`() {
        // Given
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val manager = manager()

        // When
        val entry = manager.upload(root.toString(), "photo.jpg", false, ByteArrayInputStream(bytes))

        // Then
        root.resolve("photo.jpg").readBytes() shouldBe bytes
        entry.sizeBytes shouldBe bytes.size.toLong()
        entry.mimeType shouldBe "image/jpeg"
        entry.iconId shouldBe "jpg"
    }

    @Test
    fun `refuses an upload onto an existing file unless it is told to overwrite`() {
        // Given
        root.resolve("photo.jpg").writeText("original")
        val manager = manager()

        // When
        shouldThrow<FileAlreadyThereException> {
            manager.upload(root.toString(), "photo.jpg", false, ByteArrayInputStream("new".toByteArray()))
        }

        // Then
        root.resolve("photo.jpg").readText() shouldBe "original"
        manager.upload(root.toString(), "photo.jpg", true, ByteArrayInputStream("new".toByteArray()))
        root.resolve("photo.jpg").readText() shouldBe "new"
    }

    @Test
    fun `refuses to overwrite a directory`() {
        // Given
        Files.createDirectory(root.resolve("media"))
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> {
            manager.upload(root.toString(), "media", true, ByteArrayInputStream("new".toByteArray()))
        }
    }

    @Test
    fun `stops at the upload limit even when more bytes keep coming`() {
        // Given
        val manager = manager(maxUploadBytes = 1024)

        // When
        shouldThrow<FileBrowserException> {
            manager.upload(root.toString(), "big.bin", false, ByteArrayInputStream(ByteArray(4096)))
        }

        // Then
        namesIn(root) shouldBe emptyList()
    }

    @Test
    fun `leaves nothing behind when the upload is cut off halfway`() {
        // Given
        val manager = manager()
        val failing = object : InputStream() {
            private var served = 0
            override fun read(): Int = throw IOException("connection reset")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (served > 0) {
                    throw IOException("connection reset")
                }
                served += length
                return length
            }
        }

        // When
        val thrown = shouldThrow<FileBrowserException> {
            manager.upload(root.toString(), "half.bin", false, failing)
        }

        // Then
        thrown.type shouldBe FileBrowserErrorType.IO_ERROR
        namesIn(root) shouldBe emptyList()
    }

    @Test
    fun `refuses a name that is not a single path element`() {
        // Given
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> {
            manager.upload(root.toString(), "../escape.txt", false, ByteArrayInputStream(ByteArray(0)))
        }
        namesIn(tempDir) shouldBe listOf("storage")
    }

    @Test
    fun `refuses an upload in read mode`() {
        // Given
        val manager = manager(access = FileBrowserAccess.READ)

        // When / Then
        shouldThrow<FileBrowserException> {
            manager.upload(root.toString(), "photo.jpg", false, ByteArrayInputStream(ByteArray(0)))
        }
        namesIn(root) shouldBe emptyList()
    }

    @Test
    fun `refuses an upload into a directory outside of the roots`() {
        // Given
        val manager = manager()

        // When / Then
        shouldThrow<FileBrowserException> {
            manager.upload(tempDir.toString(), "photo.jpg", false, ByteArrayInputStream(ByteArray(0)))
        }
    }

    @Test
    fun `clears out a temporary file an earlier upload abandoned`() {
        // Given
        val abandoned = root.resolve(
            "${FileBrowserManager.TEMP_FILE_PREFIX}12345${FileBrowserManager.TEMP_FILE_SUFFIX}"
        )
        abandoned.writeText("half an upload")
        Files.setLastModifiedTime(abandoned, twoHoursAgo)
        val manager = manager()

        // When
        manager.upload(root.toString(), "photo.jpg", false, ByteArrayInputStream(ByteArray(0)))

        // Then
        namesIn(root) shouldBe listOf("photo.jpg")
    }

    @Test
    fun `leaves a file of the user's own alone though its name starts the same way`() {
        // Given
        val theirs = root.resolve("${FileBrowserManager.TEMP_FILE_PREFIX}notes.txt")
        theirs.writeText("my important data")
        Files.setLastModifiedTime(theirs, twoHoursAgo)
        val manager = manager()

        // When
        manager.upload(root.toString(), "photo.jpg", false, ByteArrayInputStream(ByteArray(0)))

        // Then
        theirs.readText() shouldBe "my important data"
    }

    @Test
    fun `keeps a temporary file that is still being written`() {
        // Given
        val inFlight = root.resolve(
            "${FileBrowserManager.TEMP_FILE_PREFIX}67890${FileBrowserManager.TEMP_FILE_SUFFIX}"
        )
        inFlight.writeText("still going")
        val manager = manager()

        // When
        manager.upload(root.toString(), "photo.jpg", false, ByteArrayInputStream(ByteArray(0)))

        // Then
        inFlight.readText() shouldBe "still going"
    }
}
