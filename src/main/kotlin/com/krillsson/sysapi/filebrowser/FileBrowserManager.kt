package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.graphql.domain.PageInfo
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
import java.util.Base64
import kotlin.io.path.name

@Service
class FileBrowserManager(
    private val configuration: FileBrowserConfiguration,
    private val sandbox: FileBrowserSandbox,
    private val entryFactory: FileEntryFactory
) {

    companion object {
        const val MAX_ENTRIES = 10_000
        const val MAX_PAGE_SIZE = 1_000
        const val DEFAULT_PAGE_SIZE = 200
        const val MAX_SCANNED_ENTRIES = 500_000
        const val MAX_BATCH_SIZE = 5_000
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

        private val ORDER = compareBy<SortKey>({ !it.directory }, { it.name.lowercase() }, { it.name })
    }

    private data class SortKey(val directory: Boolean, val name: String)

    val logger by logger()

    val enabled get() = sandbox.enabled

    val access get() = sandbox.access

    fun roots(): List<FileEntry> = sandbox.roots.map { entryOf(it) }

    fun listDirectory(path: String): DirectoryListing {
        val directory = requireDirectory(path)
        val entries = mutableListOf<FileEntry>()
        var truncated = false
        readDirectory(directory) { child ->
            if (entries.size == MAX_ENTRIES) {
                truncated = true
                false
            } else {
                entries += entryOf(child)
                true
            }
        }
        return DirectoryListing(
            entry = entryOf(directory),
            entries = entries.sortedWith(compareBy({ it.type != FileEntryType.DIRECTORY }, { it.name.lowercase() })),
            truncated = truncated
        )
    }

    fun listDirectoryPage(path: String, after: String?, first: Int?): DirectoryListingConnection {
        val directory = requireDirectory(path)
        val size = (first ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val keys = mutableListOf<SortKey>()
        readDirectory(directory) { child ->
            keys += SortKey(isDirectory(child), child.name)
            keys.size < MAX_SCANNED_ENTRIES
        }
        keys.sortWith(ORDER)
        val start = after?.let { cursor ->
            val decoded = decodeCursor(cursor)
            val index = keys.binarySearch(decoded, ORDER)
            if (index >= 0) index + 1 else -index - 1
        } ?: 0
        val page = keys.drop(start).take(size)
        val edges = page.map { key ->
            FileEntryEdge(cursor = encodeCursor(key), node = entryOf(directory.resolve(key.name)))
        }
        return DirectoryListingConnection(
            entry = entryOf(directory),
            edges = edges,
            pageInfo = PageInfo(
                hasNextPage = start + page.size < keys.size,
                hasPreviousPage = start > 0,
                startCursor = edges.firstOrNull()?.cursor,
                endCursor = edges.lastOrNull()?.cursor
            ),
            totalCount = keys.size
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

    fun copy(
        source: String,
        destination: String,
        overwrite: Boolean = false,
        sink: FileOperationSink = NoFileOperationSink
    ) {
        val (from, to) = prepareCopy(source, destination, overwrite)
        runCopy(from, to, sink)
    }

    fun move(
        source: String,
        destination: String,
        overwrite: Boolean = false,
        sink: FileOperationSink = NoFileOperationSink
    ) {
        val (from, to) = prepareMove(source, destination, overwrite)
        runMove(from, to, overwrite, sink)
    }

    fun delete(path: String, recursive: Boolean, sink: FileOperationSink = NoFileOperationSink) {
        val target = prepareDelete(path, recursive)
        runDelete(target, sink)
    }

    fun prepareCopy(source: String, destination: String, overwrite: Boolean): Pair<Path, Path> {
        sandbox.requireWritable()
        val from = sandbox.resolveExisting(source)
        return from to destinationFor(from, destination, overwrite)
    }

    fun prepareMove(source: String, destination: String, overwrite: Boolean): Pair<Path, Path> {
        sandbox.requireWritable()
        val from = sandbox.resolveExisting(source)
        requireMovable(from, source)
        return from to destinationFor(from, destination, overwrite)
    }

    fun prepareDelete(path: String, recursive: Boolean): Path {
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
        }
        return target
    }

    fun runCopy(from: Path, to: Path, sink: FileOperationSink) {
        copyInto(from, to, sink)
        logger.info("Copied $from to $to")
    }

    fun runMove(from: Path, to: Path, overwrite: Boolean, sink: FileOperationSink) {
        moveInto(from, to, overwrite, sink)
        logger.info("Moved $from to $to")
    }

    fun runDelete(target: Path, sink: FileOperationSink) {
        if (Files.isDirectory(target)) {
            deleteRecursively(target, sink)
        } else {
            sink.beginFile(target.toString(), 0)
            Files.delete(target)
            sink.fileDone()
        }
        logger.info("Deleted $target")
    }

    fun targetIn(destinationDirectory: String, source: String): Path {
        val name = sandbox.resolveExisting(source).name
        return sandbox.resolveInDirectory(destinationDirectory, name)
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

    fun entryOf(path: Path): FileEntry = entryFactory.entryOf(path)

    fun requireDirectory(path: String): Path {
        val directory = sandbox.resolveExisting(path)
        if (!Files.isDirectory(directory)) {
            throw FileBrowserException("$path is not a directory")
        }
        return directory
    }

    fun copyInto(from: Path, to: Path, sink: FileOperationSink = NoFileOperationSink) {
        if (Files.isDirectory(from)) {
            copyRecursively(from, to, sink)
        } else {
            copyFile(from, to, sink)
        }
    }

    /**
     * A move that cannot be a rename falls back to copying and then deleting, so that crossing a
     * volume reports progress and can be cancelled like any other copy. Files.move would do the
     * same thing internally, but in one call that says nothing until it is over.
     *
     * A rename moves no bytes, so it counts the file's own size as moved rather than leaving a
     * progress bar sitting at nothing until the operation is suddenly over.
     */
    fun moveInto(from: Path, to: Path, overwrite: Boolean, sink: FileOperationSink = NoFileOperationSink) {
        val options = if (overwrite && Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
            arrayOf(StandardCopyOption.REPLACE_EXISTING)
        } else {
            emptyArray()
        }
        try {
            val size = if (Files.isRegularFile(from, LinkOption.NOFOLLOW_LINKS)) Files.size(from) else 0L
            sink.beginFile(from.toString(), size)
            Files.move(from, to, *options, StandardCopyOption.ATOMIC_MOVE)
            sink.addBytes(size)
            sink.fileDone()
        } catch (ex: AtomicMoveNotSupportedException) {
            copyInto(from, to, sink)
            if (Files.isDirectory(from)) {
                deleteRecursively(from, NoFileOperationSink)
            } else {
                Files.delete(from)
            }
        }
    }

    private fun copyFile(from: Path, to: Path, sink: FileOperationSink) {
        sink.beginFile(from.toString(), runCatching { Files.size(from) }.getOrDefault(0L))
        writeAtomically(to) { temp -> streamInto(from, temp, sink) }
        copyAttributesOf(from, to)
        sink.fileDone()
    }

    private fun streamInto(from: Path, to: Path, sink: FileOperationSink) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        Files.newInputStream(from).use { input ->
            FileOutputStream(to.toFile()).use { output ->
                while (true) {
                    sink.requireNotCancelled()
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                    sink.addBytes(read.toLong())
                }
                output.flush()
            }
        }
    }

    private fun copyAttributesOf(from: Path, to: Path) {
        runCatching { Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from)) }
        runCatching { Files.setLastModifiedTime(to, Files.getLastModifiedTime(from)) }
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

    private fun readDirectory(directory: Path, consume: (Path) -> Boolean) {
        try {
            Files.newDirectoryStream(directory).use { stream ->
                for (child in stream) {
                    if (sandbox.isTrash(child)) {
                        continue
                    }
                    if (!consume(child)) {
                        break
                    }
                }
            }
        } catch (ex: IOException) {
            throw FileBrowserException("$directory could not be read: ${ex.message}")
        }
    }

    private fun encodeCursor(key: SortKey): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("${if (key.directory) "d" else "f"}:${key.name}".toByteArray(StandardCharsets.UTF_8))

    private fun decodeCursor(cursor: String): SortKey {
        val decoded = runCatching {
            String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
        }.getOrNull() ?: throw FileBrowserException("$cursor is not a cursor this directory handed out")
        val marker = decoded.substringBefore(':', "")
        if (marker != "d" && marker != "f") {
            throw FileBrowserException("$cursor is not a cursor this directory handed out")
        }
        return SortKey(marker == "d", decoded.substringAfter(':'))
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

    private fun destinationFor(source: Path, destination: String, overwrite: Boolean): Path {
        val to = sandbox.resolveForCreate(destination)
        if (to == source) {
            throw FileBrowserException("$destination is the source itself")
        }
        if (to.startsWith(source)) {
            throw FileBrowserException("$destination is inside $source")
        }
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
            if (!overwrite) {
                throw FileBrowserException("$destination already exists")
            }
            if (sandbox.isRoot(to)) {
                throw FileBrowserException("$destination is one of the configured roots and cannot be replaced")
            }
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) !=
                Files.isDirectory(to, LinkOption.NOFOLLOW_LINKS)
            ) {
                throw FileBrowserException("$destination is not the same kind of thing as $source")
            }
        }
        return to
    }

    private fun requireMovable(target: Path, path: String) {
        if (sandbox.isRoot(target)) {
            throw FileBrowserException("$path is one of the configured roots and cannot be moved")
        }
        if (sandbox.isInTrash(target)) {
            throw FileBrowserException("$path is in the trash. Restore it instead")
        }
    }

    private fun isDirectory(path: Path): Boolean = runCatching {
        Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).isDirectory
    }.getOrDefault(false)

    private fun copyRecursively(from: Path, to: Path, sink: FileOperationSink) {
        Files.walkFileTree(from, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                sink.requireNotCancelled()
                Files.createDirectories(to.resolve(from.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (!attrs.isSymbolicLink) {
                    copyFile(file, to.resolve(from.relativize(file)), sink)
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun deleteRecursively(target: Path, sink: FileOperationSink) {
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                sink.requireNotCancelled()
                sink.beginFile(file.toString(), attrs.size())
                Files.delete(file)
                sink.fileDone()
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
