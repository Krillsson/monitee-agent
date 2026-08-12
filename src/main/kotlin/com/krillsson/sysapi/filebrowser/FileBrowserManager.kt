package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration
import java.time.Instant
import kotlin.io.path.name

@Service
class FileBrowserManager(
    private val configuration: FileBrowserConfiguration,
    private val sandbox: FileBrowserSandbox,
    private val fileTypeRegistry: FileTypeRegistry
) {

    companion object {
        const val MAX_ENTRIES = 10_000
        const val TEMP_FILE_PREFIX = ".monitee-upload-"
        const val TEMP_FILE_SUFFIX = ".part"

        private val ABANDONED_TEMP_FILE_AGE: Duration = Duration.ofHours(1)

        private val TEMP_FILE_NAME =
            Regex("${Regex.escape(TEMP_FILE_PREFIX)}\\d+${Regex.escape(TEMP_FILE_SUFFIX)}")

        private val DEFAULT_PERMISSIONS = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ
        )
    }

    val logger by logger()

    val enabled get() = sandbox.enabled

    val access get() = sandbox.access

    fun roots(): List<FileEntry> = sandbox.roots.map { entryOf(it) }

    fun listDirectory(path: String): DirectoryListing {
        val directory = sandbox.resolveExisting(path)
        if (!Files.isDirectory(directory)) {
            throw FileBrowserException("$path is not a directory")
        }
        val entries = mutableListOf<FileEntry>()
        var truncated = false
        try {
            Files.newDirectoryStream(directory).use { stream ->
                for (child in stream) {
                    if (entries.size == MAX_ENTRIES) {
                        truncated = true
                        break
                    }
                    entries += entryOf(child)
                }
            }
        } catch (ex: IOException) {
            throw FileBrowserException("$path could not be read: ${ex.message}")
        }
        return DirectoryListing(
            entry = entryOf(directory),
            entries = entries.sortedWith(compareBy({ it.type != FileEntryType.DIRECTORY }, { it.name.lowercase() })),
            truncated = truncated
        )
    }

    fun openTextFile(path: String): TextFileContent {
        val file = sandbox.resolveExisting(path)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$path is not a file")
        }
        val size = Files.size(file)
        if (size > configuration.maxEditableBytes) {
            throw FileBrowserException("$path is larger than the ${configuration.maxEditableBytes} byte editing limit")
        }
        val contents = decodeText(Files.readAllBytes(file))
            ?: throw FileBrowserException("$path does not look like a text file")
        return TextFileContent(entryOf(file), contents)
    }

    fun saveTextFile(path: String, contents: String) {
        sandbox.requireWritable()
        val file = sandbox.resolveForCreate(path)
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$path is not a file")
        }
        val bytes = contents.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > configuration.maxEditableBytes) {
            throw FileBrowserException("The contents are larger than the ${configuration.maxEditableBytes} byte editing limit")
        }
        writeAtomically(file) { temp -> Files.write(temp, bytes) }
        logger.info("Wrote ${bytes.size} bytes to $file")
    }

    fun copy(source: String, destination: String) {
        sandbox.requireWritable()
        val from = sandbox.resolveExisting(source)
        val to = destinationFor(from, destination)
        if (Files.isDirectory(from)) {
            copyRecursively(from, to)
        } else {
            Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES)
        }
        logger.info("Copied $from to $to")
    }

    fun move(source: String, destination: String) {
        sandbox.requireWritable()
        val from = sandbox.resolveExisting(source)
        if (sandbox.isRoot(from)) {
            throw FileBrowserException("$source is one of the configured roots and cannot be moved")
        }
        val to = destinationFor(from, destination)
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        } catch (ex: AtomicMoveNotSupportedException) {
            Files.move(from, to)
        }
        logger.info("Moved $from to $to")
    }

    fun delete(path: String, recursive: Boolean) {
        sandbox.requireWritable()
        val target = sandbox.resolveExisting(path)
        if (sandbox.isRoot(target)) {
            throw FileBrowserException("$path is one of the configured roots and cannot be deleted")
        }
        if (Files.isDirectory(target)) {
            val empty = Files.newDirectoryStream(target).use { !it.iterator().hasNext() }
            if (!empty && !recursive) {
                throw FileBrowserException("$path is not empty. Send recursive to delete it with everything in it")
            }
            deleteRecursively(target)
        } else {
            Files.delete(target)
        }
        logger.info("Deleted $target")
    }

    fun createDirectory(path: String): FileEntry {
        sandbox.requireWritable()
        val directory = sandbox.resolveForCreate(path)
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$path already exists")
        }
        Files.createDirectory(directory)
        logger.info("Created directory $directory")
        return entryOf(directory)
    }

    fun upload(directory: String, name: String, overwrite: Boolean, source: InputStream): FileEntry {
        sandbox.requireWritable()
        val target = sandbox.resolveInDirectory(directory, name)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!overwrite) {
                throw FileAlreadyThereException("${target.name} already exists in $directory")
            }
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw FileBrowserException("${target.name} is not a file and cannot be replaced by an upload")
            }
        }
        removeAbandonedTempFilesIn(requireNotNull(target.parent))
        val written = writeAtomically(target) { temp -> writeStream(source, temp) }
        logger.info("Uploaded $written bytes to $target")
        return entryOf(target)
    }

    fun openForDownload(path: String): Path {
        val file = sandbox.resolveExisting(path)
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$path is not a file")
        }
        return file
    }

    fun entryOf(path: Path): FileEntry {
        val attributes = runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull()
        val type = when {
            attributes == null -> FileEntryType.OTHER
            attributes.isSymbolicLink -> FileEntryType.SYMLINK
            attributes.isDirectory -> FileEntryType.DIRECTORY
            attributes.isRegularFile -> FileEntryType.FILE
            else -> FileEntryType.OTHER
        }
        val name = path.name.ifEmpty { path.toString() }
        val size = if (type == FileEntryType.FILE) attributes?.size() ?: 0L else 0L
        return FileEntry(
            name = name,
            path = path.toString(),
            type = type,
            sizeBytes = size,
            createdAt = attributes?.creationTime()?.toInstant()?.takeIf { it != Instant.EPOCH },
            updatedAt = attributes?.lastModifiedTime()?.toInstant(),
            mimeType = if (type == FileEntryType.FILE) fileTypeRegistry.mimeTypeOf(name) else null,
            iconId = if (type == FileEntryType.DIRECTORY) FileTypeRegistry.FOLDER_ICON else fileTypeRegistry.iconIdOf(
                name
            ),
            editable = type == FileEntryType.FILE &&
                    fileTypeRegistry.isTextual(name) &&
                    size <= configuration.maxEditableBytes,
            openableAsLog = type == FileEntryType.FILE &&
                    fileTypeRegistry.looksLikeALogFile(name) &&
                    size <= configuration.maxLogViewBytes
        )
    }

    fun <T> writeAtomically(target: Path, write: (Path) -> T): T {
        val directory = target.parent
        val temp = Files.createTempFile(directory, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
        return try {
            copyPermissionsOf(target, temp)
            val result = write(temp)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            result
        } catch (ex: Exception) {
            runCatching { Files.deleteIfExists(temp) }
            throw ex
        }
    }

    private fun writeStream(source: InputStream, target: Path): Long {
        val limit = configuration.maxUploadBytes
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        FileOutputStream(target.toFile()).use { output ->
            while (true) {
                val read = source.read(buffer)
                if (read < 0) {
                    break
                }
                written += read
                if (limit > 0 && written > limit) {
                    throw FileBrowserException("The upload is larger than the $limit byte limit")
                }
                output.write(buffer, 0, read)
            }
            output.flush()
            output.fd.sync()
        }
        return written
    }

    /**
     * Only the names createTempFile itself produces are swept, digits and all, so a file the user
     * happens to have called something starting with the same prefix is left alone.
     */
    private fun removeAbandonedTempFilesIn(directory: Path) {
        val abandonedBefore = Instant.now().minus(ABANDONED_TEMP_FILE_AGE)
        runCatching {
            Files.newDirectoryStream(directory, "$TEMP_FILE_PREFIX*$TEMP_FILE_SUFFIX").use { stream ->
                stream.filter { TEMP_FILE_NAME.matches(it.name) }
                    .filter { Files.getLastModifiedTime(it).toInstant().isBefore(abandonedBefore) }
                    .forEach { leftover ->
                        logger.info("Removing abandoned upload $leftover")
                        runCatching { Files.deleteIfExists(leftover) }
                    }
            }
        }
    }

    private fun destinationFor(source: Path, destination: String): Path {
        val to = sandbox.resolveForCreate(destination)
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$destination already exists")
        }
        if (to.startsWith(source)) {
            throw FileBrowserException("$destination is inside $source")
        }
        return to
    }

    private fun copyRecursively(from: Path, to: Path) {
        Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(to.resolve(from.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isSymbolicLink) {
                    Files.copy(file, to.resolve(from.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES)
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun deleteRecursively(target: Path) {
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                exc?.let { throw it }
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun copyPermissionsOf(original: Path, temp: Path) {
        val permissions = runCatching {
            Files.getPosixFilePermissions(original, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull() ?: DEFAULT_PERMISSIONS
        runCatching { Files.setPosixFilePermissions(temp, permissions) }
    }

    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.any { it.toInt() == 0 }) {
            return null
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val decoded: CharBuffer = decoder.decode(ByteBuffer.wrap(bytes))
            decoded.toString()
        } catch (ex: CharacterCodingException) {
            null
        }
    }
}
