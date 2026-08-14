package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

data class FileOperationRequest(
    val type: FileOperationType,
    val paths: List<String>,
    val destination: String? = null,
    val totalFiles: Int? = null,
    val totalBytes: Long? = null
)

@Service
class FileOperationRegistry(private val configuration: FileBrowserConfiguration) {

    companion object {
        private const val WORKER_THREADS = 2
        private const val MAX_RETAINED = 200

        private val EMIT_EVERY: Duration = Duration.ofMillis(250)
    }

    val logger by logger()

    private val operations = ConcurrentHashMap<String, RunningOperation>()

    private val workers = Executors.newFixedThreadPool(WORKER_THREADS) { runnable ->
        Thread(runnable, "file-operation").apply { isDaemon = true }
    }

    fun submit(request: FileOperationRequest, work: (FileOperationSink) -> Unit): FileOperation {
        sweep()
        val operation = RunningOperation(UUID.randomUUID().toString(), request)
        operations[operation.id] = operation
        workers.execute { operation.run(work) }
        return operation.snapshot()
    }

    fun all(): List<FileOperation> = operations.values
        .map { it.snapshot() }
        .sortedByDescending { it.startedAt }

    fun find(id: String): FileOperation? = operations[id]?.snapshot()

    /**
     * The sink replays its latest value, and a finished operation is kept around for
     * fileOperationRetentionMinutes, so subscribing to something that was already over answers
     * with its outcome and completes rather than waiting for an event that will never come. An id
     * this agent never held, or has since swept, completes the same way with nothing emitted,
     * since a client cannot tell the two apart and should not have to sort that out by message.
     */
    fun events(id: String): Flux<FileOperation> = operations[id]?.events() ?: Flux.empty()

    fun cancel(id: String): FileOperation {
        val operation = operations[id] ?: throw FileBrowserException("$id is not a running operation")
        operation.cancel()
        return operation.snapshot()
    }

    private fun sweep() {
        val keepUntil = Instant.now().minus(Duration.ofMinutes(configuration.fileOperationRetentionMinutes))
        operations.values
            .filter { it.finishedBefore(keepUntil) }
            .forEach { operations.remove(it.id) }
        if (operations.size > MAX_RETAINED) {
            operations.values
                .filter { it.isFinished() }
                .sortedBy { it.snapshot().finishedAt }
                .take(operations.size - MAX_RETAINED)
                .forEach { operations.remove(it.id) }
        }
    }

    private inner class RunningOperation(val id: String, private val request: FileOperationRequest) :
        FileOperationSink {

        private val sink = Sinks.many().replay().latest<FileOperation>()
        private val startedAt: Instant = Instant.now()
        private val successes = mutableListOf<String>()
        private val failures = mutableListOf<FileOperationFailure>()
        private val processedBytes = AtomicLong()
        private val processedFiles = AtomicLong()
        private val lastEmittedAt = AtomicLong(0)

        @Volatile
        private var state = FileOperationState.QUEUED

        @Volatile
        private var finishedAt: Instant? = null

        @Volatile
        private var currentPath: String? = null

        @Volatile
        private var reason: String? = null

        @Volatile
        private var cancelled = false

        init {
            emit(force = true)
        }

        fun run(work: (FileOperationSink) -> Unit) {
            state = FileOperationState.RUNNING
            emit(force = true)
            try {
                work(this)
                finish(if (cancelled) FileOperationState.CANCELLED else FileOperationState.COMPLETED, null)
            } catch (ex: FileOperationCancelledException) {
                finish(FileOperationState.CANCELLED, null)
            } catch (ex: FileBrowserException) {
                logger.info("${request.type} operation $id was refused: ${ex.message}")
                finish(FileOperationState.FAILED, ex.message.orEmpty())
            } catch (ex: Exception) {
                logger.error("${request.type} operation $id failed", ex)
                finish(FileOperationState.FAILED, ex.message ?: requireNotNull(ex::class.simpleName))
            }
        }

        fun cancel() {
            cancelled = true
        }

        fun isFinished() = state == FileOperationState.COMPLETED ||
            state == FileOperationState.FAILED ||
            state == FileOperationState.CANCELLED

        fun finishedBefore(instant: Instant) = finishedAt?.isBefore(instant) == true

        fun events(): Flux<FileOperation> = sink.asFlux()

        fun snapshot(): FileOperation = synchronized(this) {
            FileOperation(
                id = id,
                type = request.type,
                state = state,
                paths = request.paths,
                destination = request.destination,
                startedAt = startedAt,
                finishedAt = finishedAt,
                currentPath = currentPath,
                processedFiles = processedFiles.get().toInt(),
                totalFiles = request.totalFiles,
                processedBytes = processedBytes.get(),
                totalBytes = request.totalBytes,
                successes = successes.toList(),
                failures = failures.toList(),
                reason = reason
            )
        }

        override fun beginFile(path: String, sizeBytes: Long) {
            requireNotCancelled()
            currentPath = path
            emit(force = true)
        }

        override fun addBytes(bytes: Long) {
            requireNotCancelled()
            processedBytes.addAndGet(bytes)
            emit(force = false)
        }

        override fun fileDone() {
            processedFiles.incrementAndGet()
            emit(force = true)
        }

        override fun succeeded(path: String) {
            synchronized(this) { successes += path }
            emit(force = true)
        }

        override fun failed(path: String, reason: String) {
            synchronized(this) { failures += FileOperationFailure(path, reason) }
            emit(force = true)
        }

        override fun isCancelled() = cancelled

        private fun finish(state: FileOperationState, reason: String?) {
            this.state = state
            this.reason = reason
            this.currentPath = null
            this.finishedAt = Instant.now()
            emit(force = true)
            sink.tryEmitComplete()
        }

        private fun emit(force: Boolean) {
            val now = System.currentTimeMillis()
            if (!force && now - lastEmittedAt.get() < EMIT_EVERY.toMillis()) {
                return
            }
            lastEmittedAt.set(now)
            sink.tryEmitNext(snapshot())
        }
    }
}
