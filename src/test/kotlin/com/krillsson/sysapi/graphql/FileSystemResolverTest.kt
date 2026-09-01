package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.domain.history.SystemHistoryEntry
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.metrics.FileSystemMetrics
import com.krillsson.sysapi.core.metrics.Metrics
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class FileSystemResolverTest {

    private val fileSystemMetrics: FileSystemMetrics = mockk()
    private val metrics: Metrics = mockk {
        every { fileSystemMetrics() } returns fileSystemMetrics
    }
    private val historyRepository: HistoryRepository = mockk()
    private val now: Instant = Instant.parse("2026-01-15T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val resolver = FileSystemResolver(metrics, historyRepository, clock)

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

    @Test
    fun `fetches history once for the whole batch and forecasts each filesystem independently`() {
        // Given
        val growing = fileSystem("growing", totalSpaceBytes = 100_000)
        val flat = fileSystem("flat", totalSpaceBytes = 100_000)
        val history = (0..9).map { day ->
            historyEntry(
                date = now.minus(Duration.ofDays((9 - day).toLong())),
                fileSystemLoads = listOf(
                    FileSystemLoad("growing", "growing", 90_000 - day * 1_000L, 0, 100_000),
                    FileSystemLoad("flat", "flat", 90_000, 0, 100_000)
                )
            )
        }
        every { historyRepository.getExtendedHistoryLimitedToDates(any(), any()) } returns history

        // When
        val result = resolver.spaceForecast(listOf(growing, flat))

        // Then
        result[growing].shouldNotBeNull()
        result[flat].shouldBeNull()
        verify(exactly = 1) { historyRepository.getExtendedHistoryLimitedToDates(any(), any()) }
    }

    @Test
    fun `maps a filesystem with no matching history entries to null instead of dropping it`() {
        // Given
        val known = fileSystem("known")
        val unknown = fileSystem("unknown")
        every { historyRepository.getExtendedHistoryLimitedToDates(any(), any()) } returns listOf(
            historyEntry(now, listOf(FileSystemLoad("known", "known", 1, 2, 3)))
        )

        // When
        val result = resolver.spaceForecast(listOf(known, unknown))

        // Then
        result[unknown] shouldBe null
    }

    private fun historyEntry(date: Instant, fileSystemLoads: List<FileSystemLoad>): SystemHistoryEntry {
        val historySystemLoad: HistorySystemLoad = mockk {
            every { this@mockk.fileSystemLoads } returns fileSystemLoads
        }
        return mockk {
            every { this@mockk.date } returns date
            every { this@mockk.value } returns historySystemLoad
        }
    }

    private fun fileSystem(id: String, totalSpaceBytes: Long = 0) = FileSystem(
        name = id,
        id = id,
        description = "",
        label = "",
        type = "ext4",
        volume = "",
        mount = "/$id",
        totalSpaceBytes = totalSpaceBytes
    )
}
