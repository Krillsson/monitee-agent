package com.krillsson.sysapi.filebrowser

import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Everything that can take a while is validated on the calling thread and then run on a worker,
 * so a path outside a root or a read-only agent is still refused immediately, with a reason,
 * rather than turning into an operation that fails a moment later.
 */
@Service
class FileOperationService(
    private val sandbox: FileBrowserSandbox,
    private val manager: FileBrowserManager,
    private val archiveService: ArchiveService,
    private val trashService: TrashService,
    private val registry: FileOperationRegistry,
    private val treeWalker: FileTreeWalker
) {

    companion object {
        const val MAX_BATCH_SIZE = 5_000
    }

    fun copy(source: String, destination: String, overwrite: Boolean): FileOperation {
        val (from, to) = manager.prepareCopy(source, destination, overwrite)
        return single(FileOperationType.COPY, from, to.toString()) { sink -> manager.runCopy(from, to, sink) }
    }

    fun move(source: String, destination: String, overwrite: Boolean): FileOperation {
        val (from, to) = manager.prepareMove(source, destination, overwrite)
        return single(FileOperationType.MOVE, from, to.toString()) { sink ->
            manager.runMove(from, to, overwrite, sink)
        }
    }

    fun delete(path: String, recursive: Boolean): FileOperation {
        val target = manager.prepareDelete(path, recursive)
        return single(FileOperationType.DELETE, target, null, countBytes = false) { sink ->
            manager.runDelete(target, sink)
        }
    }

    fun copyAll(sources: List<String>, destinationDirectory: String, overwrite: Boolean): FileOperation =
        many(FileOperationType.COPY, sources, destinationDirectory) { source, sink ->
            val (from, to) = manager.prepareCopy(
                source,
                manager.targetIn(destinationDirectory, source).toString(),
                overwrite
            )
            manager.runCopy(from, to, sink)
        }

    fun moveAll(sources: List<String>, destinationDirectory: String, overwrite: Boolean): FileOperation =
        many(FileOperationType.MOVE, sources, destinationDirectory) { source, sink ->
            val (from, to) = manager.prepareMove(
                source,
                manager.targetIn(destinationDirectory, source).toString(),
                overwrite
            )
            manager.runMove(from, to, overwrite, sink)
        }

    fun deleteAll(paths: List<String>, recursive: Boolean): FileOperation =
        many(FileOperationType.DELETE, paths, null, countBytes = false) { path, sink ->
            manager.runDelete(manager.prepareDelete(path, recursive), sink)
        }

    fun extractArchive(
        path: String,
        destinationDirectory: String,
        overwrite: Boolean,
        entries: List<String> = emptyList()
    ): FileOperation {
        val archive = archiveService.prepareExtract(path)
        val destination = manager.requireDirectory(destinationDirectory)
        return registry.submit(
            FileOperationRequest(
                type = FileOperationType.EXTRACT_ARCHIVE,
                paths = listOf(archive.toString()),
                destination = destination.toString(),
                measure = { archiveService.measureExtract(archive, entries) }
            )
        ) { sink ->
            archiveService.extract(archive.toString(), destination.toString(), overwrite, entries, sink)
            sink.succeeded(archive.toString())
        }
    }

    fun createArchive(sources: List<String>, destination: String, overwrite: Boolean): FileOperation {
        val (roots, target) = archiveService.prepareCreate(sources, destination, overwrite)
        return registry.submit(
            FileOperationRequest(
                type = FileOperationType.CREATE_ARCHIVE,
                paths = roots.map { it.toString() },
                destination = target.toString(),
                measure = { sink -> treeWalker.measure(roots, countBytes = true, countDirectories = true, sink) }
            )
        ) { sink ->
            archiveService.create(roots.map { it.toString() }, target.toString(), overwrite, sink)
            sink.succeeded(target.toString())
        }
    }

    fun emptyTrash(id: String?): FileOperation {
        trashService.requireEmptyable(id)
        return registry.submit(
            FileOperationRequest(type = FileOperationType.EMPTY_TRASH, paths = listOfNotNull(id))
        ) { sink -> trashService.empty(id, sink) }
    }

    fun all(): List<FileOperation> = registry.all()

    fun find(id: String): FileOperation? = registry.find(id)

    fun events(id: String): Flux<FileOperation> = registry.events(id)

    fun cancel(id: String): FileOperation = registry.cancel(id)

    private fun single(
        type: FileOperationType,
        path: Path,
        destination: String?,
        countBytes: Boolean = true,
        run: (FileOperationSink) -> Unit
    ): FileOperation = registry.submit(request(type, listOf(path.toString()), destination, countBytes)) { sink ->
        run(sink)
        sink.succeeded(path.toString())
    }

    /**
     * Each path is resolved inside the operation rather than before it, so one that cannot be
     * copied is recorded against itself and the rest of the batch still runs. Refusing all 200
     * because the 137th name is taken is exactly what having a batch is meant to avoid. Only what
     * is wrong with the request as a whole is checked up front, and only cancellation stops it
     * part way through.
     */
    private fun many(
        type: FileOperationType,
        paths: List<String>,
        destination: String?,
        countBytes: Boolean = true,
        run: (String, FileOperationSink) -> Unit
    ): FileOperation {
        sandbox.requireWritable()
        if (paths.isEmpty()) {
            throw FileBrowserException("No paths were given")
        }
        if (paths.size > MAX_BATCH_SIZE) {
            throw FileBrowserException("A batch cannot hold more than $MAX_BATCH_SIZE paths")
        }
        return registry.submit(request(type, paths, destination, countBytes)) { sink ->
            paths.forEach { path ->
                sink.requireNotCancelled()
                try {
                    run(path, sink)
                    sink.succeeded(path)
                } catch (ex: FileOperationCancelledException) {
                    throw ex
                } catch (ex: Exception) {
                    sink.failed(path, ex.message ?: requireNotNull(ex::class.simpleName))
                }
            }
        }
    }

    /**
     * Totals are claimed outright when every path is a regular file, since its size was there to
     * be read while resolving it. A directory means walking it, which is done lazily as a
     * MEASURING step once the operation has a worker, rather than paying for the walk on the
     * calling thread before the request even returns.
     */
    private fun request(
        type: FileOperationType,
        paths: List<String>,
        destination: String?,
        countBytes: Boolean
    ): FileOperationRequest {
        val resolved = paths.mapNotNull { path -> runCatching { sandbox.resolveExisting(path) }.getOrNull() }
        if (resolved.size != paths.size) {
            return FileOperationRequest(type = type, paths = paths, destination = destination)
        }
        val sizes = resolved.map { path ->
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) Files.size(path) else null
        }
        if (sizes.none { it == null }) {
            return FileOperationRequest(
                type = type,
                paths = paths,
                destination = destination,
                totalFiles = sizes.size,
                totalBytes = if (countBytes) sizes.filterNotNull().sum() else null
            )
        }
        return FileOperationRequest(
            type = type,
            paths = paths,
            destination = destination,
            measure = { sink -> treeWalker.measure(resolved, countBytes, countDirectories = false, sink) }
        )
    }
}
