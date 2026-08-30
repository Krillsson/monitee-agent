package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.metrics.FileSystemMetrics
import com.krillsson.sysapi.core.metrics.Metrics
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class FileSystemResolverTest {

    private val fileSystemMetrics: FileSystemMetrics = mockk()
    private val metrics: Metrics = mockk {
        every { fileSystemMetrics() } returns fileSystemMetrics
    }
    private val resolver = FileSystemResolver(metrics)

    @Test
    fun `resolves metrics for every filesystem with a single loads lookup`() {
        // Given
        val a = fileSystem("a")
        val b = fileSystem("b")
        val loadA = FileSystemLoad("a", "a", 1, 2, 3)
        val loadB = FileSystemLoad("b", "b", 4, 5, 6)
        every { fileSystemMetrics.fileSystemLoads() } returns listOf(loadA, loadB)

        // When
        val result = resolver.metrics(listOf(a, b))

        // Then
        result shouldContain (a to loadA)
        result shouldContain (b to loadB)
        verify(exactly = 1) { fileSystemMetrics.fileSystemLoads() }
    }

    @Test
    fun `maps a filesystem without a matching load to null instead of dropping it`() {
        // Given
        val onlyKnown = fileSystem("known")
        val unknown = fileSystem("unknown")
        every { fileSystemMetrics.fileSystemLoads() } returns listOf(
            FileSystemLoad("known", "known", 1, 2, 3)
        )

        // When
        val result = resolver.metrics(listOf(onlyKnown, unknown))

        // Then
        result[unknown] shouldBe null
    }

    private fun fileSystem(id: String) = FileSystem(
        name = id,
        id = id,
        description = "",
        label = "",
        type = "ext4",
        volume = "",
        mount = "/$id",
        totalSpaceBytes = 0
    )
}
