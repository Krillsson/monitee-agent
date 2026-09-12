package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceTrend
import com.krillsson.sysapi.core.history.series.HistoryResolution
import com.krillsson.sysapi.core.history.series.MetricHistory
import com.krillsson.sysapi.core.history.series.MetricHistoryPoint
import com.krillsson.sysapi.core.history.series.MetricHistoryService
import com.krillsson.sysapi.core.history.series.MetricId
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
    private val metricHistoryService: MetricHistoryService = mockk()
    private val forecastDAO: FileSystemSpaceForecastDAO = mockk(relaxed = true)
    private val now: Instant = Instant.parse("2026-01-15T00:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val recorder = FileSystemSpaceForecastRecorder(metrics, metricHistoryService, forecastDAO, clock)

    @Test
    fun `computes and saves a forecast for every filesystem with enough history, growing or not`() {
        // Given
        val growing = fileSystem("growing")
        val flat = fileSystem("flat")
        every { fileSystemMetrics.fileSystems() } returns listOf(growing, flat)
        givenDailyUsedBytes("growing") { day -> 10_000 + day * 1_000L }
        givenDailyUsedBytes("flat") { 10_000L }

        val saved = slot<List<FileSystemSpaceForecastEntity>>()
        every { forecastDAO.saveAll(capture(saved)) } returns emptyList()

        // When
        recorder.run()

        // Then
        saved.captured shouldHaveSize 2
        val byId = saved.captured.associateBy { it.filesystemId }
        byId.getValue("growing").trend shouldBe FileSystemSpaceTrend.GROWING
        byId.getValue("flat").trend shouldBe FileSystemSpaceTrend.STABLE
        byId.getValue("growing").computedAt shouldBe now
        verify(exactly = 1) { forecastDAO.deleteAllByFilesystemIdIn(listOf("growing", "flat")) }
    }

    @Test
    fun `asks for the daily tier over the full forecast window`() {
        // Given
        val fileSystem = fileSystem("data")
        every { fileSystemMetrics.fileSystems() } returns listOf(fileSystem)
        givenDailyUsedBytes("data") { day -> 10_000 + day * 1_000L }
        every {
            forecastDAO.saveAll(any<List<FileSystemSpaceForecastEntity>>())
        } returns emptyList<FileSystemSpaceForecastEntity>()

        // When
        recorder.run()

        // Then
        verify {
            metricHistoryService.history(
                MetricId.FILESYSTEM_USED_BYTES,
                "data",
                now.minus(Duration.ofDays(30)),
                now,
                HistoryResolution.DAILY
            )
        }
    }

    @Test
    fun `saves nothing for a filesystem without enough history yet`() {
        // Given
        val tooNew = fileSystem("tooNew")
        every { fileSystemMetrics.fileSystems() } returns listOf(tooNew)
        givenDailyUsedBytes("tooNew", days = 3) { day -> 10_000 + day * 1_000L }

        val saved = slot<List<FileSystemSpaceForecastEntity>>()
        every { forecastDAO.saveAll(capture(saved)) } returns emptyList()

        // When
        recorder.run()

        // Then
        saved.captured.shouldBeEmpty()
    }

    @Test
    fun `saves nothing for a filesystem the series holds no points for`() {
        // Given
        val unknown = fileSystem("unknown")
        every { fileSystemMetrics.fileSystems() } returns listOf(unknown)
        every {
            metricHistoryService.history(MetricId.FILESYSTEM_USED_BYTES, "unknown", any(), any(), any())
        } returns MetricHistory(HistoryResolution.DAILY, now.minus(Duration.ofDays(30)), now, emptyList())

        val saved = slot<List<FileSystemSpaceForecastEntity>>()
        every { forecastDAO.saveAll(capture(saved)) } returns emptyList()

        // When
        recorder.run()

        // Then
        saved.captured.shouldBeEmpty()
    }

    private fun givenDailyUsedBytes(id: String, days: Int = 10, usedBytes: (Int) -> Long) {
        val points = (0 until days).map { day ->
            MetricHistoryPoint(
                timestamp = now.minus(Duration.ofDays((days - 1 - day).toLong())),
                samples = 288,
                min = usedBytes(day).toDouble(),
                avg = usedBytes(day).toDouble(),
                max = usedBytes(day).toDouble(),
                last = usedBytes(day).toDouble()
            )
        }
        every {
            metricHistoryService.history(MetricId.FILESYSTEM_USED_BYTES, id, any(), any(), any())
        } returns MetricHistory(HistoryResolution.DAILY, now.minus(Duration.ofDays(30)), now, points)
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
