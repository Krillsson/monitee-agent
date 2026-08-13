package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.util.FileSystem
import com.krillsson.sysapi.util.logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.path.name

/**
 * ImageIO reaches AWT's Toolkit the first time it is touched, and the native image cannot load
 * that JNI library. The failure is a fatal JNI error that takes the process down rather than
 * something a caller can catch, so the native build must not reach ImageIO at all, not even to
 * ask whether it has a JPEG writer.
 */
object ThumbnailSupport {

    private const val NATIVE_IMAGE_CODE = "org.graalvm.nativeimage.imagecode"

    val available: Boolean get() = System.getProperty(NATIVE_IMAGE_CODE) == null
}

@Service
class ThumbnailService(
    private val configuration: FileBrowserConfiguration,
    private val sandbox: FileBrowserSandbox,
    private val fileTypeRegistry: FileTypeRegistry,
    private val cache: Path
) {

    companion object {
        const val MIN_SIZE = 32
        const val MAX_SIZE = 512
        const val DEFAULT_SIZE = 256
        const val MEDIA_TYPE = "image/jpeg"

        private const val CACHE_DIRECTORY = "thumbnails"
        private const val SUFFIX = ".jpg"
        private const val QUALITY = 0.82f
    }

    @Autowired
    constructor(
        configuration: FileBrowserConfiguration,
        sandbox: FileBrowserSandbox,
        fileTypeRegistry: FileTypeRegistry
    ) : this(configuration, sandbox, fileTypeRegistry, FileSystem.data.toPath().resolve(CACHE_DIRECTORY))

    val logger by logger()

    fun thumbnail(path: String, size: Int?): Path {
        if (!configuration.thumbnails) {
            throw FileBrowserException("Thumbnails are switched off")
        }
        if (!ThumbnailSupport.available) {
            throw UnsupportedThumbnailException("This build of the agent cannot render images")
        }
        val file = sandbox.resolveExisting(path)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$path is not a file")
        }
        if (!fileTypeRegistry.isThumbnailable(file.name)) {
            throw UnsupportedThumbnailException("${file.name} is not an image this agent can render")
        }
        val sourceBytes = Files.size(file)
        if (sourceBytes > configuration.maxThumbnailSourceBytes) {
            throw UnsupportedThumbnailException(
                "${file.name} is larger than the ${configuration.maxThumbnailSourceBytes} byte thumbnail limit"
            )
        }
        val edge = (size ?: DEFAULT_SIZE).coerceIn(MIN_SIZE, MAX_SIZE)
        val cached = cachedFile(file, sourceBytes, edge)
        if (Files.isRegularFile(cached)) {
            return cached
        }
        render(file, cached, edge)
        evictIfOverBudget()
        return cached
    }

    private fun cachedFile(file: Path, sourceBytes: Long, edge: Int): Path {
        val digest = MessageDigest.getInstance("SHA-256")
        val modified = runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(0L)
        digest.update("$file|$modified|$sourceBytes|$edge".toByteArray(StandardCharsets.UTF_8))
        return cache.resolve(HexFormat.of().formatHex(digest.digest()) + SUFFIX)
    }

    private fun render(file: Path, cached: Path, edge: Int) {
        ImageIO.setUseCache(false)
        val source = read(file)
        val scaled = scale(source, edge)
        Files.createDirectories(cache)
        val temp = Files.createTempFile(cache, ".rendering-", SUFFIX)
        try {
            if (!write(scaled, temp)) {
                throw UnsupportedThumbnailException("${file.name} could not be encoded as a thumbnail")
            }
            Files.move(temp, cached, StandardCopyOption.REPLACE_EXISTING)
        } catch (ex: Exception) {
            runCatching { Files.deleteIfExists(temp) }
            throw ex
        }
    }

    private fun read(file: Path): BufferedImage {
        val image = try {
            Files.newInputStream(file).buffered().use { input -> ImageIO.read(input) }
        } catch (ex: IOException) {
            throw UnsupportedThumbnailException("${file.name} could not be read as an image: ${ex.message}")
        }
        return image ?: throw UnsupportedThumbnailException("${file.name} is not an image this agent can render")
    }

    private fun write(image: BufferedImage, target: Path): Boolean =
        Files.newOutputStream(target).buffered().use { output ->
            val writers = ImageIO.getImageWritersByFormatName("jpg")
            if (!writers.hasNext()) {
                return false
            }
            val writer = writers.next()
            val stream = ImageIO.createImageOutputStream(output) ?: return false
            try {
                writer.output = stream
                val parameters = writer.defaultWriteParam
                if (parameters.canWriteCompressed()) {
                    parameters.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    parameters.compressionQuality = QUALITY
                }
                writer.write(null, IIOImage(image, null, null), parameters)
            } finally {
                writer.dispose()
                stream.close()
            }
            true
        }

    /**
     * Halved repeatedly before the last step, because one bilinear pass from a 6000 pixel photo
     * straight down to 256 reads so few of the source pixels that the result visibly aliases.
     */
    private fun scale(source: BufferedImage, edge: Int): BufferedImage {
        val longest = maxOf(source.width, source.height)
        if (longest <= edge) {
            return paint(source, source.width, source.height)
        }
        val ratio = edge.toDouble() / longest
        val targetWidth = maxOf(1, Math.round(source.width * ratio).toInt())
        val targetHeight = maxOf(1, Math.round(source.height * ratio).toInt())
        var current = source
        var width = source.width
        var height = source.height
        while (width / 2 > targetWidth && height / 2 > targetHeight) {
            width /= 2
            height /= 2
            current = paint(current, width, height)
        }
        return paint(current, targetWidth, targetHeight)
    }

    private fun paint(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = target.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return target
    }

    private fun evictIfOverBudget() {
        runCatching {
            val budget = configuration.maxThumbnailCacheBytes
            val files = Files.newDirectoryStream(cache).use { stream -> stream.toList() }
            var total = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
            if (total <= budget) {
                return
            }
            files.sortedBy { runCatching { Files.getLastModifiedTime(it).toMillis() }.getOrDefault(0L) }
                .forEach { oldest ->
                    if (total <= budget * 4 / 5) {
                        return@runCatching
                    }
                    total -= runCatching { Files.size(oldest) }.getOrDefault(0L)
                    runCatching { Files.deleteIfExists(oldest) }
                }
        }
    }
}
