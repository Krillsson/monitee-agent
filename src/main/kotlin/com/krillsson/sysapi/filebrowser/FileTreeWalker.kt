package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class FileSearchInput(
    val path: String,
    val query: String,
    val maxResults: Int = 500,
    val maxDepth: Int = 16,
    val includeDirectories: Boolean = true
)

@Service
class FileTreeWalker(
    private val configuration: FileBrowserConfiguration,
    private val sandbox: FileBrowserSandbox,
    private val entryFactory: FileEntryFactory
) {

    companion object {
        const val MAX_RESULTS = 5_000
        const val MAX_DEPTH = 64
    }

    private class BudgetExhausted : RuntimeException(null, null, false, false)

    fun search(query: FileSearchInput): FileSearchResult {
        val directory = requireDirectory(query.path)
        val needle = query.query.trim()
        if (needle.isEmpty()) {
            throw FileBrowserException("A search needs something to look for")
        }
        val startedAt = System.nanoTime()
        val deadline = startedAt + configuration.searchTimeoutSeconds * 1_000_000_000
        val found = mutableListOf<Path>()
        val limit = query.maxResults.coerceIn(1, MAX_RESULTS)
        val truncated = walk(directory, query.maxDepth.coerceIn(1, MAX_DEPTH), deadline) { path, attributes ->
            val isDirectory = attributes.isDirectory
            if ((query.includeDirectories || !isDirectory) &&
                path != directory &&
                path.fileName.toString().contains(needle, ignoreCase = true)
            ) {
                found.add(path)
                if (found.size >= limit) {
                    throw BudgetExhausted()
                }
            }
        }
        return FileSearchResult(
            entries = found.map { entryFactory.entryOf(it) },
            truncated = truncated,
            durationMillis = (System.nanoTime() - startedAt) / 1_000_000
        )
    }

    fun directorySize(path: String, maxDepth: Int): DirectorySize {
        val directory = requireDirectory(path)
        val deadline = System.nanoTime() + configuration.searchTimeoutSeconds * 1_000_000_000
        var totalBytes = 0L
        var fileCount = 0
        var directoryCount = 0
        val truncated = walk(directory, maxDepth.coerceIn(1, MAX_DEPTH), deadline) { walked, attributes ->
            when {
                attributes.isSymbolicLink -> Unit
                attributes.isDirectory -> if (walked != directory) directoryCount++
                attributes.isRegularFile -> {
                    fileCount++
                    totalBytes += attributes.size()
                }
            }
        }
        return DirectorySize(
            entry = entryFactory.entryOf(directory),
            totalBytes = totalBytes,
            fileCount = fileCount,
            directoryCount = directoryCount,
            truncated = truncated
        )
    }

    /**
     * The totals an operation could not know without a walk. Bounded by the same deadline and
     * MAX_DEPTH as directorySize, so a tree too large to size in time gives up and returns null
     * rather than a total that stopped partway through. Cancelling the operation while this is
     * still walking aborts it the same way cancelling the work itself would, via the sink.
     */
    fun measure(paths: List<Path>, countBytes: Boolean, countDirectories: Boolean, sink: FileOperationSink): PathTotals? {
        val deadline = System.nanoTime() + configuration.searchTimeoutSeconds * 1_000_000_000
        var files = 0
        var bytes = 0L
        paths.forEach { path ->
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                val truncated = walk(path, MAX_DEPTH, deadline) { _, attributes ->
                    sink.requireNotCancelled()
                    when {
                        attributes.isRegularFile -> {
                            files++
                            if (countBytes) bytes += attributes.size()
                        }

                        attributes.isDirectory && countDirectories -> files++
                    }
                }
                if (truncated) {
                    return null
                }
            } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                files++
                if (countBytes) bytes += runCatching { Files.size(path) }.getOrDefault(0L)
            }
        }
        return PathTotals(files, if (countBytes) bytes else null)
    }

    private fun requireDirectory(path: String): Path {
        val directory = sandbox.resolveExisting(path)
        if (!Files.isDirectory(directory)) {
            throw FileBrowserException("$path is not a directory")
        }
        return directory
    }

    /**
     * Symbolic links are not followed, which both matches what the sandbox allows and makes a
     * cycle impossible, so the only ways out are the depth limit, the caller's own limit and the
     * wall clock. The clock is what stops a walk over an array of millions of files holding a
     * request thread for the rest of the day.
     *
     * walkFileTree hands a directory that sits at maxDepth to visitFile rather than descending
     * into it, which is how the walk notices it was cut short.
     */
    private fun walk(
        directory: Path,
        maxDepth: Int,
        deadline: Long,
        visit: (Path, BasicFileAttributes) -> Unit
    ): Boolean {
        var truncated = false
        try {
            Files.walkFileTree(directory, emptySet(), maxDepth, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (sandbox.isTrash(dir)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return visited(dir, attrs)
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isDirectory) {
                        truncated = true
                    }
                    return visited(file, attrs)
                }

                override fun visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE

                override fun postVisitDirectory(dir: Path, exc: IOException?) = FileVisitResult.CONTINUE

                private fun visited(path: Path, attributes: BasicFileAttributes): FileVisitResult {
                    if (System.nanoTime() > deadline) {
                        throw BudgetExhausted()
                    }
                    visit(path, attributes)
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (ex: BudgetExhausted) {
            truncated = true
        } catch (ex: IOException) {
            throw FileBrowserException("$directory could not be walked: ${ex.message}")
        }
        return truncated
    }
}
