package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.name

data class ArchiveExtraction(val entry: FileEntry, val entryCount: Int, val totalBytes: Long)

@Service
class ArchiveService(
    private val configuration: FileBrowserConfiguration,
    private val sandbox: FileBrowserSandbox,
    private val manager: FileBrowserManager,
    private val fileTypeRegistry: FileTypeRegistry
) {

    val logger by logger()

    private class Budget(private val maxEntries: Int, private val maxBytes: Long) {
        var entries = 0
            private set
        var bytes = 0L
            private set

        fun countEntry() {
            entries++
            if (entries > maxEntries) {
                throw FileBrowserException("The archive holds more than $maxEntries entries")
            }
        }

        fun countBytes(written: Long) {
            bytes += written
            if (bytes > maxBytes) {
                throw FileBrowserException("The archive unpacks to more than $maxBytes bytes")
            }
        }
    }

    fun extract(path: String, destinationDirectory: String, overwrite: Boolean): ArchiveExtraction {
        sandbox.requireWritable()
        val archive = sandbox.resolveExisting(path)
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$path is not a file")
        }
        val format = fileTypeRegistry.archiveFormatOf(archive.name)
            ?: throw FileBrowserException("${archive.name} is not an archive this agent can open")
        val destination = manager.requireDirectory(destinationDirectory)
        val budget = Budget(configuration.maxArchiveEntries, configuration.maxArchiveBytes)
        Files.newInputStream(archive).buffered().use { input ->
            when (format) {
                ArchiveFormat.ZIP -> extractZip(input, destination, overwrite, budget)
                ArchiveFormat.TAR -> extractTar(input, destination, overwrite, budget)
                ArchiveFormat.TAR_GZ -> extractTar(GZIPInputStream(input), destination, overwrite, budget)
                ArchiveFormat.GZIP -> extractGzip(input, archive, destination, overwrite, budget)
            }
        }
        logger.info("Extracted ${budget.entries} entries of $archive into $destination")
        return ArchiveExtraction(manager.entryOf(destination), budget.entries, budget.bytes)
    }

    fun create(sources: List<String>, destination: String, overwrite: Boolean): FileEntry {
        sandbox.requireWritable()
        if (sources.isEmpty()) {
            throw FileBrowserException("No paths were given")
        }
        if (fileTypeRegistry.archiveFormatOf(destination) != ArchiveFormat.ZIP) {
            throw FileBrowserException("$destination is not a zip. Only zip archives can be created")
        }
        val target = sandbox.resolveForCreate(destination)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!overwrite) {
                throw FileBrowserException("$destination already exists")
            }
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw FileBrowserException("$destination is not a file and cannot be replaced by an archive")
            }
        }
        val roots = sources.map { sandbox.resolveExisting(it) }
        roots.firstOrNull { target.startsWith(it) }
            ?.let { throw FileBrowserException("$destination would be inside $it, which it is archiving") }
        val budget = Budget(configuration.maxArchiveEntries, configuration.maxArchiveBytes)
        manager.writeAtomically(target) { temp ->
            ZipOutputStream(Files.newOutputStream(temp).buffered()).use { zip ->
                roots.forEach { source -> addToZip(zip, source, budget) }
            }
        }
        logger.info("Archived ${budget.entries} entries into $target")
        return manager.entryOf(target)
    }

    private fun extractZip(input: InputStream, destination: Path, overwrite: Boolean, budget: Budget) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                budget.countEntry()
                val target = targetOf(destination, entry.name)
                if (entry.isDirectory) {
                    createDirectory(target)
                } else {
                    writeEntry(zip, target, overwrite, budget)
                }
                zip.closeEntry()
            }
        }
    }

    private fun extractTar(input: InputStream, destination: Path, overwrite: Boolean, budget: Budget) {
        TarArchive(BufferedInputStream(input)).forEach { entry, data ->
            budget.countEntry()
            val target = targetOf(destination, entry.name)
            if (entry.directory) {
                createDirectory(target)
            } else {
                writeEntry(data, target, overwrite, budget)
            }
        }
    }

    private fun extractGzip(
        input: InputStream,
        archive: Path,
        destination: Path,
        overwrite: Boolean,
        budget: Budget
    ) {
        val name = archive.name.dropLast(".gz".length)
        budget.countEntry()
        GZIPInputStream(input).use { data -> writeEntry(data, targetOf(destination, name), overwrite, budget) }
    }

    /**
     * The whole of zip slip lives here. A name is refused outright rather than normalised when it
     * is absolute or holds a traversal or a separator the local filesystem would not have written,
     * and what is left still has to resolve inside the destination and inside a configured root.
     */
    private fun targetOf(destination: Path, name: String): Path {
        val segments = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty()) {
            throw FileBrowserException("The archive holds an entry without a name")
        }
        if (name.startsWith("/") || name.startsWith("\\") || segments.any { it == ".." }) {
            throw FileBrowserException("The archive holds an entry that points outside of it: $name")
        }
        segments.forEach { sandbox.validName(it) }
        val target = segments.fold(destination) { path, segment -> path.resolve(segment) }.normalize()
        if (!target.startsWith(destination) || !sandbox.contains(target)) {
            throw FileBrowserException("The archive holds an entry that points outside of it: $name")
        }
        return target
    }

    private fun createDirectory(target: Path) {
        requireNoSymlinkAbove(target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw FileBrowserException("$target is already there and is not a directory")
        }
        Files.createDirectories(target)
    }

    private fun writeEntry(data: InputStream, target: Path, overwrite: Boolean, budget: Budget) {
        createDirectory(requireNotNull(target.parent))
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!overwrite) {
                throw FileAlreadyThereException("$target is already there")
            }
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw FileBrowserException("$target is already there and is not a file")
            }
        }
        manager.writeAtomically(target) { temp ->
            Files.newOutputStream(temp).buffered().use { output -> copy(data, output, budget) }
        }
    }

    private fun copy(data: InputStream, output: OutputStream, budget: Budget) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = data.read(buffer)
            if (read < 0) {
                break
            }
            output.write(buffer, 0, read)
            budget.countBytes(read.toLong())
        }
        output.flush()
    }

    /**
     * An entry can only land under directories the extraction itself made or that were already
     * plain directories, so an archive cannot use a link someone left in the destination to write
     * through it. The sandbox refuses a link on the way in, but the destination is walked here
     * entry by entry rather than resolved once.
     */
    private fun requireNoSymlinkAbove(target: Path) {
        var walked = target
        while (sandbox.contains(walked)) {
            if (Files.isSymbolicLink(walked)) {
                throw FileBrowserException("$walked is a symbolic link, which the file browser does not follow")
            }
            walked = walked.parent ?: break
        }
    }

    private fun entryNameOf(base: Path, path: Path) = base.relativize(path).joinToString("/")

    private fun addToZip(zip: ZipOutputStream, source: Path, budget: Budget) {
        val base = requireNotNull(source.parent)
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (sandbox.isTrash(dir)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                budget.countEntry()
                zip.putNextEntry(ZipEntry(entryNameOf(base, dir) + "/"))
                zip.closeEntry()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isSymbolicLink || !attrs.isRegularFile) {
                    return FileVisitResult.CONTINUE
                }
                budget.countEntry()
                val entry = ZipEntry(entryNameOf(base, file))
                entry.lastModifiedTime = attrs.lastModifiedTime()
                zip.putNextEntry(entry)
                Files.newInputStream(file).use { input -> copy(input, zip, budget) }
                zip.closeEntry()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE
        })
    }
}
