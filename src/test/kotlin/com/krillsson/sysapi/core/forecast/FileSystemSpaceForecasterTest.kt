package com.krillsson.sysapi.core.forecast

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
        forecast.growthBytesPerDay shouldBe 1_000.0
        // last recorded used = 10_000 + 9*1_000 = 19_000, remaining = 81_000, at 1_000/day = 81 days
        forecast.daysUntilFull shouldBe 81.0
        forecast.daysUntilFullLow shouldBe 81.0
        forecast.daysUntilFullHigh shouldBe 81.0
        forecast.daysOfHistoryUsed shouldBe 9.0
    }

    @Test
    fun `returns null when usage is flat`() {
        // Given
        val points = dailyPoints(days = 10, startBytes = 10_000, bytesPerDay = 0)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldBeNull()
    }

    @Test
    fun `returns null when usage is shrinking`() {
        // Given
        val points = dailyPoints(days = 10, startBytes = 50_000, bytesPerDay = -1_000)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000, points.last().first)

        // Then
        forecast.shouldBeNull()
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
    fun `returns null when the projected fill date is beyond the sanity ceiling`() {
        // Given: total space dwarfs the growth rate, so it would take centuries to fill
        val points = dailyPoints(days = 10, startBytes = 10_000, bytesPerDay = 1)

        // When
        val forecast = FileSystemSpaceForecaster.forecast(points, 100_000_000_000, points.last().first)

        // Then
        forecast.shouldBeNull()
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
        forecast.daysUntilFullLow shouldBeLessThan forecast.daysUntilFull
        forecast.daysUntilFull shouldBeLessThan forecast.daysUntilFullHigh
        forecast.daysUntilFullLow shouldBeGreaterThan 0.0
    }
}
