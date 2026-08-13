package com.krillsson.sysapi.filebrowser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.writeText

class ThumbnailServiceTest {

    private companion object {
        const val NATIVE_IMAGE_CODE = "org.graalvm.nativeimage.imagecode"
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path

    private lateinit var cache: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
        cache = tempDir.resolve("cache")
    }

    @AfterEach
    fun tearDown() {
        System.clearProperty(NATIVE_IMAGE_CODE)
    }

    private fun service(thumbnails: Boolean = true, maxThumbnailSourceBytes: Long = 67_108_864) = fileBrowser(
        root,
        cache = cache,
        thumbnails = thumbnails,
        maxThumbnailSourceBytes = maxThumbnailSourceBytes
    ).thumbnails

    private fun image(name: String, width: Int, height: Int): Path {
        val file = root.resolve(name)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        Files.newOutputStream(file).use { ImageIO.write(image, name.substringAfterLast('.'), it) }
        return file
    }

    private fun sizeOf(thumbnail: Path): Pair<Int, Int> {
        val image = Files.newInputStream(thumbnail).use { ImageIO.read(it) }
        return image.width to image.height
    }

    @Test
    fun `scales the longest side down to the size that was asked for, keeping the aspect ratio`() {
        // Given
        val file = image("wide.png", 1000, 400)

        // When
        val thumbnail = service().thumbnail(file.toString(), 100)

        // Then
        sizeOf(thumbnail) shouldBe (100 to 40)
    }

    @Test
    fun `leaves an image smaller than the size that was asked for alone`() {
        // Given
        val file = image("small.png", 40, 20)

        // When
        val thumbnail = service().thumbnail(file.toString(), 256)

        // Then
        sizeOf(thumbnail) shouldBe (40 to 20)
    }

    @Test
    fun `keeps the size within the range it is willing to render`() {
        // Given
        val file = image("wide.jpg", 1000, 1000)

        // When
        val huge = service().thumbnail(file.toString(), 5000)
        val tiny = service().thumbnail(file.toString(), 1)

        // Then
        sizeOf(huge) shouldBe (ThumbnailService.MAX_SIZE to ThumbnailService.MAX_SIZE)
        sizeOf(tiny) shouldBe (ThumbnailService.MIN_SIZE to ThumbnailService.MIN_SIZE)
    }

    @Test
    fun `renders once and answers from the cache after that`() {
        // Given
        val file = image("photo.png", 400, 400)
        val service = service()

        // When
        val first = service.thumbnail(file.toString(), 128)
        val renderedAt = Files.getLastModifiedTime(first)
        val second = service.thumbnail(file.toString(), 128)

        // Then
        second shouldBe first
        Files.getLastModifiedTime(second) shouldBe renderedAt
        Files.list(cache).use { it.toList() }.size shouldBe 1
    }

    @Test
    fun `renders again when the file behind it changes`() {
        // Given
        val file = image("photo.png", 400, 400)
        val service = service()
        val first = service.thumbnail(file.toString(), 128)

        // When
        Files.delete(file)
        image("photo.png", 200, 100)
        val second = service.thumbnail(file.toString(), 128)

        // Then
        second shouldNotBe first
        sizeOf(second) shouldBe (128 to 64)
    }

    @Test
    fun `refuses anything that is not an image it renders`() {
        // Given
        val file = root.resolve("notes.txt")
        file.writeText("content")

        // When / Then
        shouldThrow<UnsupportedThumbnailException> { service().thumbnail(file.toString(), 128) }
    }

    @Test
    fun `refuses a file that only looks like an image`() {
        // Given
        val file = root.resolve("notes.png")
        file.writeText("not a png at all")

        // When / Then
        shouldThrow<UnsupportedThumbnailException> { service().thumbnail(file.toString(), 128) }
        Files.exists(cache) shouldBe false
    }

    @Test
    fun `refuses an image larger than the limit, and refuses everything when it is switched off`() {
        // Given
        val file = image("photo.png", 400, 400)

        // When / Then
        shouldThrow<UnsupportedThumbnailException> {
            service(maxThumbnailSourceBytes = 16).thumbnail(file.toString(), 128)
        }
        shouldThrow<FileBrowserException> { service(thumbnails = false).thumbnail(file.toString(), 128) }
    }

    @Test
    fun `refuses to render at all in a build that cannot reach ImageIO`() {
        // Given
        val file = image("photo.png", 400, 400)
        System.setProperty(NATIVE_IMAGE_CODE, "runtime")

        // When / Then
        ThumbnailSupport.available shouldBe false
        shouldThrow<UnsupportedThumbnailException> { service().thumbnail(file.toString(), 128) }
        Files.exists(cache) shouldBe false
    }

    @Test
    fun `does not offer a thumbnail on an entry in a build that cannot reach ImageIO`() {
        // Given
        val file = image("photo.png", 400, 400)

        // When / Then
        fileBrowser(root, cache = cache).entryFactory.entryOf(file).hasThumbnail shouldBe true
        System.setProperty(NATIVE_IMAGE_CODE, "runtime")
        fileBrowser(root, cache = cache).entryFactory.entryOf(file).hasThumbnail shouldBe false
    }
}
