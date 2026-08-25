package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.nio.file.Path
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
    val totalBytes: Long? = null,
    val measure: ((FileOperationSink) -> PathTotals?)? = null,
    val checkRoom: ((Long) -> Unit)? = null
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
        val accepted = operation.snapshot()
        workers.execute { operation.run(work) }
        return accepted
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
        val operation = operations[id]
            ?: throw FileBrowserException("$id is not a running operation", FileBrowserErrorType.NOT_FOUND)
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
        private val throughput = ThroughputMeter()

        @Volatile
        private var state = FileOperationState.QUEUED

        @Volatile
        private var finishedAt: Instant? = null

        @Volatile
        private var currentPath: String? = null

        @Volatile
        private var reason: String? = null

        @Volatile
        private var errorType: FileBrowserErrorType? = null

        @Volatile
        private var cancelled = false

        @Volatile
        private var totalFiles: Int? = request.totalFiles

        @Volatile
        private var totalBytes: Long? = request.totalBytes

        @Volatile
        private var bytesPerSecond: Long? = null

        init {
            emit(force = true)
        }

        fun run(work: (FileOperationSink) -> Unit) {
            try {
                request.measure?.let { measure ->
                    state = FileOperationState.MEASURING
                    emit(force = true)
                    val totals = measure(this)
                    totalFiles = totals?.files
                    totalBytes = totals?.bytes
                }
                totalBytes?.let { bytes -> request.checkRoom?.invoke(bytes) }
                state = FileOperationState.RUNNING
                emit(force = true)
                work(this)
                finishWithOutcome()
            } catch (ex: FileOperationCancelledException) {
                finish(FileOperationState.CANCELLED, null, null)
            } catch (ex: Exception) {
                val failure = FileBrowserErrors.describe(ex, destination())
                log(failure, ex)
                finish(FileOperationState.FAILED, failure.message.orEmpty(), failure.type)
            }
        }

        /**
         * A batch attempts every path, so some of them failing is still a completed operation. All
         * of them failing is not, and reporting that as COMPLETED tells a client that nothing went
         * wrong when nothing went right.
         */
        private fun finishWithOutcome() {
            val failed = synchronized(this) { if (successes.isEmpty()) failures.toList() else emptyList() }
            when {
                cancelled -> finish(FileOperationState.CANCELLED, null, null)
                failed.isEmpty() -> finish(FileOperationState.COMPLETED, null, null)
                else -> {
                    val reason = failed.map { it.reason }.distinct().singleOrNull()
                        ?: "None of the ${failed.size} paths could be handled"
                    logger.warn("${request.type} operation $id failed on every one of its paths: $reason")
                    finish(
                        FileOperationState.FAILED,
                        reason,
                        failed.map { it.type }.distinct().singleOrNull() ?: FileBrowserErrorType.IO_ERROR
                    )
                }
            }
        }

        private fun log(failure: FileBrowserException, ex: Throwable) {
            when (failure.type) {
                FileBrowserErrorType.REFUSED ->
                    logger.info("${request.type} operation $id was refused: ${failure.message}")

                FileBrowserErrorType.IO_ERROR ->
                    logger.error("${request.type} operation $id failed on ${asked()}", ex)

                else ->
                    logger.warn("${request.type} operation $id failed on ${asked()}: ${failure.message}")
            }
        }

        private fun asked(): String = request.paths.singleOrNull() ?: "${request.paths.size} paths"

        private fun destination(): Path? =
            request.destination?.let { runCatching { Path.of(it) }.getOrNull() }

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
                totalFiles = totalFiles,
                processedBytes = processedBytes.get(),
                totalBytes = totalBytes,
                bytesPerSecond = bytesPerSecond,
                successes = successes.toList(),
                failures = failures.toList(),
                reason = reason,
                errorType = errorType
            )
        }

        override fun beginFile(path: String, sizeBytes: Long) {
            requireNotCancelled()
            currentPath = path
            emit(force = true)
        }

        override fun addBytes(bytes: Long) {
            requireNotCancelled()
            val total = processedBytes.addAndGet(bytes)
            bytesPerSecond = throughput.rateFor(total)
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

        override fun failed(path: String, reason: String, type: FileBrowserErrorType) {
            synchronized(this) { failures += FileOperationFailure(path, reason, type) }
            logger.warn("${request.type} operation $id could not handle $path: $reason")
            emit(force = true)
        }

        override fun isCancelled() = cancelled

        private fun finish(state: FileOperationState, reason: String?, errorType: FileBrowserErrorType?) {
            this.state = state
            this.reason = reason
            this.errorType = errorType
            this.currentPath = null
            this.finishedAt = Instant.now()
            this.bytesPerSecond = null
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

    /**
     * A rate averaged over the last few seconds of samples rather than the two most recent, so a
     * client that only ever sees throttled or reconnect snapshots still gets a stable number
     * instead of whatever the last 250ms happened to move.
     */
    private class ThroughputMeter {

        companion object {
            private val WINDOW = Duration.ofSeconds(5)
        }

        private data class Sample(val atNanos: Long, val bytes: Long)

        private val samples = ArrayDeque<Sample>()

        @Synchronized
        fun rateFor(totalBytes: Long): Long? {
            val now = System.nanoTime()
            samples.addLast(Sample(now, totalBytes))
            val cutoff = now - WINDOW.toNanos()
            while (samples.size > 1 && samples.first().atNanos < cutoff) {
                samples.removeFirst()
            }
            val oldest = samples.first()
            val elapsedNanos = now - oldest.atNanos
            val bytesMoved = totalBytes - oldest.bytes
            if (elapsedNanos <= 0 || bytesMoved <= 0) {
                return null
            }
            return bytesMoved * 1_000_000_000L / elapsedNanos
        }
    }
}
