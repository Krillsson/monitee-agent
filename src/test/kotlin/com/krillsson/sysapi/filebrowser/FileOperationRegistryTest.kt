package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserConfiguration
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
}
