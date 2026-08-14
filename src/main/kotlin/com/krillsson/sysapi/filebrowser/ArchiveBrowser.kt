package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.name

/**
 * Looking inside an archive without unpacking it, which only zip can answer cheaply: its central
 * directory is an index, so listing a folder and reading one file out of it are both a seek away.
 * tar has no index and a gzipped tar cannot be seeked at all, so both would mean reading the whole
 * thing to answer one screen, and are refused rather than served slowly.
 */
@Service
class ArchiveBrowser(
    private val configuration: FileBrowserConfiguration,
    private val sandbox: FileBrowserSandbox,
    private val entryFactory: FileEntryFactory,
    private val fileTypeRegistry: FileTypeRegistry
) {

    companion object {
        const val MAX_ENTRIES = 10_000
    }

    fun list(path: String, entryPath: String?): ArchiveListing {
        val archive = requireBrowsable(path)
        val prefix = prefixOf(entryPath)
        val children = LinkedHashMap<String, ArchiveEntry>()
        var scanned = 0
        var truncated = false
        var sawPrefix = prefix.isEmpty()
        open(archive) { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                scanned++
                if (scanned > configuration.maxArchiveEntries) {
                    truncated = true
                    break
                }
                val name = entry.name.replace('\\', '/')
                if (!name.startsWith(prefix)) {
                    continue
                }
                sawPrefix = true
                val remainder = name.removePrefix(prefix)
                if (remainder.isEmpty()) {
                    continue
                }
                if (children.size >= MAX_ENTRIES) {
                    truncated = true
                    break
                }
                val separator = remainder.indexOf('/')
                when {
                    separator < 0 -> children[remainder] = entryOf(prefix, remainder, entry)
                    separator == remainder.length - 1 -> {
                        val directory = remainder.dropLast(1)
                        children[directory] = entryOf(prefix, directory, entry)
                    }

                    else -> {
                        val directory = remainder.substring(0, separator)
                        children.putIfAbsent(directory, directoryOf(prefix, directory))
                    }
                }
            }
        }
        if (!sawPrefix) {
            throw FileBrowserException("${prefix.trimEnd('/')} is not in ${archive.name}")
        }
        return ArchiveListing(
            entry = entryFactory.entryOf(archive),
            entryPath = prefix.trimEnd('/'),
            entries = children.values.sortedWith(
                compareBy({ it.type != FileEntryType.DIRECTORY }, { it.name.lowercase() })
            ),
            truncated = truncated
        )
    }

    fun entryOf(path: String, entryPath: String): ArchiveEntry {
        val archive = requireBrowsable(path)
        var found: ArchiveEntry? = null
        open(archive) { zip ->
            val entry = zip.getEntry(entryPath)
                ?: throw FileBrowserException("$entryPath is not in ${archive.name}")
            if (entry.isDirectory) {
                throw FileBrowserException("$entryPath is a directory inside ${archive.name}")
            }
            found = entryOf(entryPath.substringBeforeLast('/', ""), entry.name.substringAfterLast('/'), entry)
        }
        return requireNotNull(found)
    }

    /**
     * The archive is opened again rather than held between listing the entry and streaming it, so
     * a response that is never read cannot leave a file handle behind. Reading the central
     * directory a second time is a seek, not a scan.
     */
    fun <T> readEntry(path: String, entryPath: String, read: (InputStream) -> T): T {
        val archive = requireBrowsable(path)
        return ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry(entryPath)
                ?: throw FileBrowserException("$entryPath is not in ${archive.name}")
            if (entry.isDirectory) {
                throw FileBrowserException("$entryPath is a directory inside ${archive.name}")
            }
            try {
                zip.getInputStream(entry).use(read)
            } catch (ex: ZipException) {
                throw FileBrowserException("$entryPath could not be read out of ${archive.name}: ${ex.message}")
            }
        }
    }

    private fun requireBrowsable(path: String): Path {
        val archive = sandbox.resolveExisting(path)
        if (fileTypeRegistry.archiveFormatOf(archive.name) != ArchiveFormat.ZIP) {
            throw FileBrowserException("${archive.name} is not a zip. Only a zip can be looked into without unpacking it")
        }
        return archive
    }

    private fun open(archive: Path, read: (ZipFile) -> Unit) {
        try {
            ZipFile(archive.toFile()).use(read)
        } catch (ex: ZipException) {
            throw FileBrowserException("${archive.name} could not be read as a zip: ${ex.message}")
        } catch (ex: IOException) {
            throw FileBrowserException("${archive.name} could not be read: ${ex.message}")
        }
    }

    private fun prefixOf(entryPath: String?): String {
        val trimmed = entryPath.orEmpty().replace('\\', '/').trim('/')
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed.split('/').any { it == "." || it == ".." }) {
            throw FileBrowserException("$entryPath is not a path inside an archive")
        }
        return "$trimmed/"
    }

    private fun entryOf(prefix: String, name: String, entry: ZipEntry) = ArchiveEntry(
        name = name,
        entryPath = prefix + name,
        type = if (entry.isDirectory) FileEntryType.DIRECTORY else FileEntryType.FILE,
        sizeBytes = entry.size.coerceAtLeast(0),
        compressedBytes = entry.compressedSize.coerceAtLeast(0),
        updatedAt = runCatching { entry.lastModifiedTime?.toInstant() }.getOrNull(),
        mimeType = if (entry.isDirectory) null else fileTypeRegistry.mimeTypeOf(name),
        iconId = if (entry.isDirectory) FileTypeRegistry.FOLDER_ICON else fileTypeRegistry.iconIdOf(name)
    )

    private fun directoryOf(prefix: String, name: String) = ArchiveEntry(
        name = name,
        entryPath = prefix + name,
        type = FileEntryType.DIRECTORY,
        sizeBytes = 0,
        compressedBytes = 0,
        updatedAt = null,
        mimeType = null,
        iconId = FileTypeRegistry.FOLDER_ICON
    )
}
