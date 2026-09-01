package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecast
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecastHistoryPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object FileSystemSpaceForecaster {

    private const val MIN_POINTS = 3
    private const val MIN_HISTORY_DAYS = 7.0
    private const val MAX_FORECAST_DAYS = 365.0 * 10

    fun forecast(
        points: List<Pair<Instant, Long>>,
        totalSpaceBytes: Long,
        now: Instant
    ): FileSystemSpaceForecast? {
        val sorted = points.sortedBy { it.first }
        if (sorted.size < MIN_POINTS) return null

        val firstTimestamp = sorted.first().first
        val daysOfHistoryUsed = Duration.between(firstTimestamp, sorted.last().first).toSeconds() / 86400.0
        if (daysOfHistoryUsed < MIN_HISTORY_DAYS) return null

        val xs = sorted.map { Duration.between(firstTimestamp, it.first).toSeconds() / 86400.0 }
        val ys = sorted.map { it.second.toDouble() }

        val regression = ordinaryLeastSquares(xs, ys) ?: return null
        if (regression.slope <= 0.0) return null

        val currentUsedBytes = ys.last()
        val remainingBytes = (totalSpaceBytes - currentUsedBytes).coerceAtLeast(0.0)

        val daysUntilFull = remainingBytes / regression.slope
        if (daysUntilFull > MAX_FORECAST_DAYS) return null

        // One standard error, not a 95% CI - this is a rough range for a phone notification,
        // not a statistical claim, and widening it to 1.96 SE reads as more precise than the
        // handful of history points backing it actually supports.
        val fastSlope = regression.slope + regression.slopeStandardError
        val slowSlope = regression.slope - regression.slopeStandardError

        val daysUntilFullLow = remainingBytes / fastSlope
        val daysUntilFullHigh = if (slowSlope <= 0.0) {
            MAX_FORECAST_DAYS
        } else {
            (remainingBytes / slowSlope).coerceAtMost(MAX_FORECAST_DAYS)
        }

        return FileSystemSpaceForecast(
            growthBytesPerDay = regression.slope,
            daysUntilFull = daysUntilFull,
            daysUntilFullLow = daysUntilFullLow,
            daysUntilFullHigh = daysUntilFullHigh,
            projectedFullDate = now.plusSeconds((daysUntilFull * 86400).toLong()),
            daysOfHistoryUsed = daysOfHistoryUsed,
            history = thinToOnePerDay(sorted)
        )
    }

    private fun thinToOnePerDay(sorted: List<Pair<Instant, Long>>): List<FileSystemSpaceForecastHistoryPoint> =
        sorted
            .groupBy { it.first.atZone(ZoneId.systemDefault()).toLocalDate() }
            .values
            .map { it.last() }
            .map { (date, usedBytes) -> FileSystemSpaceForecastHistoryPoint(date, usedBytes) }

    private data class Regression(val slope: Double, val intercept: Double, val slopeStandardError: Double)

    private fun ordinaryLeastSquares(xs: List<Double>, ys: List<Double>): Regression? {
        val n = xs.size
        val meanX = xs.average()
        val meanY = ys.average()

        val sumXX = xs.sumOf { (it - meanX) * (it - meanX) }
        if (sumXX == 0.0) return null

        val sumXY = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
        val slope = sumXY / sumXX
        val intercept = meanY - slope * meanX

        val sumSquaredError = xs.indices.sumOf {
            val predicted = intercept + slope * xs[it]
            val error = ys[it] - predicted
            error * error
        }
        val residualVariance = sumSquaredError / (n - 2)
        val slopeStandardError = kotlin.math.sqrt(residualVariance / sumXX)

        return Regression(slope, intercept, slopeStandardError)
    }
}
