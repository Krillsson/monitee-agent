package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FileOperationRegistryTest {

    private val registry = FileOperationRegistry(FileBrowserConfiguration(enabled = true))

    @Test
    fun `flips from QUEUED to RUNNING once a worker picks up the operation`() {
        // Given
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        // When
        val operation = registry.submit(FileOperationRequest(FileOperationType.DELETE, listOf("a"))) {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
        }

        // Then
        started.await(5, TimeUnit.SECONDS)
        registry.find(operation.id)?.state shouldBe FileOperationState.RUNNING

        // Cleanup
        release.countDown()
    }

    @Test
    fun `leaves a third operation QUEUED while the two workers are already busy`() {
        // Given
        val bothStarted = CountDownLatch(2)
        val release = CountDownLatch(1)
        repeat(2) { index ->
            registry.submit(FileOperationRequest(FileOperationType.DELETE, listOf("blocker-$index"))) {
                bothStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        bothStarted.await(5, TimeUnit.SECONDS)

        // When
        val third = registry.submit(FileOperationRequest(FileOperationType.DELETE, listOf("c"))) {}

        // Then
        third.state shouldBe FileOperationState.QUEUED

        // When
        release.countDown()
        val finished = registry.events(third.id).blockLast(Duration.ofSeconds(5))

        // Then
        finished?.state shouldBe FileOperationState.COMPLETED
    }

    @Test
    fun `completes without emitting instead of throwing for an id it has never held`() {
        // When / Then
        registry.events("not-an-operation").blockLast(Duration.ofSeconds(5)) shouldBe null
    }

    @Test
    fun `walks through MEASURING before RUNNING when the request carries a measure step`() {
        // Given
        val measuring = CountDownLatch(1)
        val release = CountDownLatch(1)

        // When
        val operation = registry.submit(
            FileOperationRequest(
                FileOperationType.COPY,
                listOf("a"),
                measure = {
                    measuring.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    PathTotals(3, 30)
                }
            )
        ) {}

        // Then
        measuring.await(5, TimeUnit.SECONDS)
        registry.find(operation.id)?.state shouldBe FileOperationState.MEASURING

        // When
        release.countDown()
        val finished = registry.events(operation.id).blockLast(Duration.ofSeconds(5))

        // Then
        finished?.state shouldBe FileOperationState.COMPLETED
        finished?.totalFiles shouldBe 3
        finished?.totalBytes shouldBe 30
    }

    @Test
    fun `falls back to null totals when the measure step gives up`() {
        // When
        val operation = registry.submit(FileOperationRequest(FileOperationType.COPY, listOf("a"), measure = { null })) {}
        val finished = registry.events(operation.id).blockLast(Duration.ofSeconds(5))

        // Then
        finished?.totalFiles shouldBe null
        finished?.totalBytes shouldBe null
    }

    @Test
    fun `cancelling while MEASURING stops the operation before the work itself ever runs`() {
        // Given
        val measuring = CountDownLatch(1)

        // When
        val operation = registry.submit(
            FileOperationRequest(
                FileOperationType.COPY,
                listOf("a"),
                measure = { sink ->
                    measuring.countDown()
                    while (!sink.isCancelled()) Thread.sleep(5)
                    sink.requireNotCancelled()
                    null
                }
            )
        ) { error("the work must not run once the operation was cancelled while measuring") }
        measuring.await(5, TimeUnit.SECONDS)
        registry.cancel(operation.id)
        val finished = registry.events(operation.id).blockLast(Duration.ofSeconds(5))

        // Then
        finished?.state shouldBe FileOperationState.CANCELLED
    }

    @Test
    fun `reports a smoothed bytesPerSecond while bytes are moving, and clears it once finished`() {
        // Given
        val movedTwoChunks = CountDownLatch(1)
        val release = CountDownLatch(1)

        // When
        val operation = registry.submit(FileOperationRequest(FileOperationType.COPY, listOf("a"))) { sink ->
            sink.beginFile("a", 100)
            sink.addBytes(50)
            Thread.sleep(20)
            sink.addBytes(50)
            movedTwoChunks.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        movedTwoChunks.await(5, TimeUnit.SECONDS)

        // Then
        registry.find(operation.id)?.bytesPerSecond.shouldNotBeNull()

        // When
        release.countDown()
        val finished = registry.events(operation.id).blockLast(Duration.ofSeconds(5))

        // Then
        finished?.bytesPerSecond shouldBe null
    }
}
