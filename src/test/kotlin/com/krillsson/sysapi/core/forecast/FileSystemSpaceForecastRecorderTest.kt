package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.domain.history.SystemHistoryEntry
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.metrics.FileSystemMetrics
import com.krillsson.sysapi.core.metrics.Metrics
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class FileSystemSpaceForecastRecorderTest {

    private val fileSystemMetrics: FileSystemMetrics = mockk()
    private val metrics: Metrics = mockk {
        every { fileSystemMetrics() } returns fileSystemMetrics
    }
    private val historyRepository: HistoryRepository = mockk()
    private val forecastDAO: FileSystemSpaceForecastDAO = mockk(relaxed = true)
    private val now: Instant = Instant.parse("2026-01-15T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val recorder = FileSystemSpaceForecastRecorder(metrics, historyRepository, forecastDAO, clock)

    @Test
    fun `computes and saves a forecast per growing filesystem, skipping ones with no real trend`() {
        // Given
        val growing = fileSystem("growing")
        val flat = fileSystem("flat")
        every { fileSystemMetrics.fileSystems() } returns listOf(growing, flat)
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

        val saved = slot<List<FileSystemSpaceForecastEntity>>()
        every { forecastDAO.saveAll(capture(saved)) } returns emptyList()

        // When
        recorder.run()

        // Then
        saved.captured shouldHaveSize 1
        saved.captured.single().filesystemId shouldBe "growing"
        saved.captured.single().computedAt shouldBe now
        verify(exactly = 1) { forecastDAO.deleteAllByFilesystemIdIn(listOf("growing", "flat")) }
    }

    @Test
    fun `saves nothing when no filesystem has a real trend`() {
        // Given
        val flat = fileSystem("flat")
        every { fileSystemMetrics.fileSystems() } returns listOf(flat)
        val history = (0..9).map { day ->
            historyEntry(
                date = now.minus(Duration.ofDays((9 - day).toLong())),
                fileSystemLoads = listOf(FileSystemLoad("flat", "flat", 90_000, 0, 100_000))
            )
        }
        every { historyRepository.getExtendedHistoryLimitedToDates(any(), any()) } returns history

        val saved = slot<List<FileSystemSpaceForecastEntity>>()
        every { forecastDAO.saveAll(capture(saved)) } returns emptyList()

        // When
        recorder.run()

        // Then
        saved.captured.shouldBeEmpty()
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

    private fun fileSystem(id: String, totalSpaceBytes: Long = 100_000) = FileSystem(
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
