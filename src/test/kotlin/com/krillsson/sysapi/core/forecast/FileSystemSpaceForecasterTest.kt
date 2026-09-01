package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceTrend
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class FileSystemSpaceForecasterTest {

    private val baseTimestamp: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun dailyPoints(days: Int, startBytes: Long, bytesPerDay: Long): List<Pair<Instant, Long>> =
        (0 until days).map { day -> baseTimestamp.plus(Duration.ofDays(day.toLong())) to (startBytes + day * bytesPerDay) }

    @Test
    fun `projects a full date from a perfectly linear fill`() {
        // Given
        val points = dailyPoints(days = 10, startBytes = 10_000, bytesPerDay = 1_000)
        val totalSpaceBytes = 100_000L
        val now = points.last().first

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, totalSpaceBytes, now)

        // Then
        forecast.shouldNotBeNull()
        forecast.trend shouldBe FileSystemSpaceTrend.GROWING
        forecast.growthBytesPerDay shouldBe 1_000.0
        // last recorded used = 10_000 + 9*1_000 = 19_000, remaining = 81_000, at 1_000/day = 81 days
        forecast.daysUntilFull shouldBe 81.0
        forecast.daysUntilFullLow shouldBe 81.0
        forecast.daysUntilFullHigh shouldBe 81.0
        forecast.projectedFullDate.shouldNotBeNull()
        forecast.daysOfHistoryUsed shouldBe 9.0
    }

    @Test
    fun `reports STABLE with no days-until-full when usage is flat`() {
        // Given
        val points = dailyPoints(days = 10, startBytes = 10_000, bytesPerDay = 0)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        forecast.trend shouldBe FileSystemSpaceTrend.STABLE
        forecast.growthBytesPerDay shouldBe 0.0
        forecast.daysUntilFull.shouldBeNull()
        forecast.daysUntilFullLow.shouldBeNull()
        forecast.daysUntilFullHigh.shouldBeNull()
        forecast.projectedFullDate.shouldBeNull()
    }

    @Test
    fun `reports SHRINKING with no days-until-full when usage is decreasing`() {
        // Given
        val points = dailyPoints(days = 10, startBytes = 50_000, bytesPerDay = -1_000)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        forecast.trend shouldBe FileSystemSpaceTrend.SHRINKING
        forecast.growthBytesPerDay shouldBe -1_000.0
        forecast.daysUntilFull.shouldBeNull()
        forecast.daysUntilFullLow.shouldBeNull()
        forecast.daysUntilFullHigh.shouldBeNull()
        forecast.projectedFullDate.shouldBeNull()
    }

    @Test
    fun `returns null with fewer than 7 days of history span`() {
        // Given
        val points = dailyPoints(days = 5, startBytes = 10_000, bytesPerDay = 1_000)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldBeNull()
    }

    @Test
    fun `returns null with fewer than 3 points even if they span enough days`() {
        // Given
        val points = listOf(
            baseTimestamp to 10_000L,
            baseTimestamp.plus(Duration.ofDays(10)) to 20_000L
        )

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldBeNull()
    }

    @Test
    fun `returns a GROWING forecast even when the projected fill date is decades away, as long as growth is real`() {
        // Given: a huge volume with a small but perfectly steady (noise-free) daily growth -
        // real signal, just a slow one relative to capacity. This is the shape of a large NAS
        // array: reproduces a real case where an earlier day-count ceiling wrongly hid it.
        val points = dailyPoints(days = 10, startBytes = 10_000, bytesPerDay = 1)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000_000_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        forecast.trend shouldBe FileSystemSpaceTrend.GROWING
        val daysUntilFull = forecast.daysUntilFull.shouldNotBeNull()
        daysUntilFull shouldBeGreaterThan 365.0 * 10
    }

    @Test
    fun `reports STABLE when a positive drift is not distinguishable from noise`() {
        // Given: a real zigzag pattern with a small upward drift (200 bytes/day) that's
        // dwarfed by the swing (+-2000 bytes) between samples - a human eye might call this
        // "kind of going up", but it isn't statistically distinguishable from flat.
        val amplitude = 2_000L
        val driftPerDay = 200L
        val points = (0 until 10).map { day ->
            val zigzag = if (day % 2 == 0) amplitude else -amplitude
            baseTimestamp.plus(Duration.ofDays(day.toLong())) to (10_000L + zigzag + driftPerDay * day)
        }

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        forecast.trend shouldBe FileSystemSpaceTrend.STABLE
        forecast.daysUntilFull.shouldBeNull()
    }

    @Test
    fun `brackets the nominal estimate with a low-high range on noisy history`() {
        // Given
        val noisyBytesPerDay = listOf(800L, 1_200L, 900L, 1_100L, 1_000L, 1_300L, 700L, 1_050L, 950L, 1_150L)
        var used = 10_000L
        val points = noisyBytesPerDay.mapIndexed { day, bytesAdded ->
            used += bytesAdded
            baseTimestamp.plus(Duration.ofDays(day.toLong())) to used
        }

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 200_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        val daysUntilFull = forecast.daysUntilFull.shouldNotBeNull()
        val daysUntilFullLow = forecast.daysUntilFullLow.shouldNotBeNull()
        val daysUntilFullHigh = forecast.daysUntilFullHigh.shouldNotBeNull()
        daysUntilFullLow shouldBeLessThan daysUntilFull
        daysUntilFull shouldBeLessThan daysUntilFullHigh
        daysUntilFullLow shouldBeGreaterThan 0.0
    }

    @Test
    fun `includes a non-empty daily history when a forecast is returned`() {
        // Given
        val points = dailyPoints(days = 10, startBytes = 10_000, bytesPerDay = 1_000)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        forecast.history shouldHaveSize 10
    }

    @Test
    fun `includes daily history even when the trend is STABLE, for the app to still chart`() {
        // Given
        val points = dailyPoints(days = 10, startBytes = 10_000, bytesPerDay = 0)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        forecast.history shouldHaveSize 10
    }

    @Test
    fun `thins multiple samples per day down to the last sample of each day`() {
        // Given: 4 samples per day (one every 6 hours) for 10 days
        val samplesPerDay = 4
        val points = (0 until 10 * samplesPerDay).map { sample ->
            baseTimestamp.plus(Duration.ofHours((sample * 6).toLong())) to (10_000L + sample * 250L)
        }

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldNotBeNull()
        (forecast.history.size < points.size) shouldBe true
        (forecast.history.size <= 10) shouldBe true
        forecast.history.last().usedBytes shouldBe points.last().second
    }
}
