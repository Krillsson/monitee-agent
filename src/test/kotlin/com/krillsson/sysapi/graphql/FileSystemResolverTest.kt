package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceTrend
import com.krillsson.sysapi.core.forecast.FileSystemSpaceForecastDAO
import com.krillsson.sysapi.core.forecast.FileSystemSpaceForecastEntity
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
import java.time.Instant

class FileSystemResolverTest {

    private val fileSystemMetrics: FileSystemMetrics = mockk()
    private val metrics: Metrics = mockk {
        every { fileSystemMetrics() } returns fileSystemMetrics
    }
    private val forecastDAO: FileSystemSpaceForecastDAO = mockk()
    private val resolver = FileSystemResolver(metrics, forecastDAO)

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
    fun `resolves the precomputed forecast for every filesystem with a single lookup`() {
        // Given
        val growing = fileSystem("growing")
        val flat = fileSystem("flat")
        val entity = forecastEntity("growing")
        every { forecastDAO.findAllById(listOf("growing", "flat")) } returns listOf(entity)

        // When
        val result = resolver.spaceForecast(listOf(growing, flat))

        // Then
        result[growing].shouldNotBeNull()
        result[flat].shouldBeNull()
        verify(exactly = 1) { forecastDAO.findAllById(listOf("growing", "flat")) }
    }

    @Test
    fun `maps a filesystem with no precomputed forecast to null instead of dropping it`() {
        // Given
        val known = fileSystem("known")
        val unknown = fileSystem("unknown")
        every { forecastDAO.findAllById(listOf("known", "unknown")) } returns listOf(forecastEntity("known"))

        // When
        val result = resolver.spaceForecast(listOf(known, unknown))

        // Then
        result[unknown] shouldBe null
    }

    private fun forecastEntity(filesystemId: String) = FileSystemSpaceForecastEntity(
        filesystemId = filesystemId,
        computedAt = Instant.parse("2026-01-15T00:00:00Z"),
        trend = FileSystemSpaceTrend.GROWING,
        growthBytesPerDay = 1_000.0,
        daysUntilFull = 81.0,
        daysUntilFullLow = 70.0,
        daysUntilFullHigh = 95.0,
        projectedFullDate = Instant.parse("2026-04-06T00:00:00Z"),
        daysOfHistoryUsed = 9.0,
        history = emptyList()
    )

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
