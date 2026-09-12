package com.krillsson.sysapi.core.history.series

import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MetricSeriesBucketsTest {

    @Test
    fun `merges a band out of the extremes of its parts`() {
        // Given
        val buckets = listOf(
            seriesBucket(MetricResolution.RAW, at("2026-09-12T10:00:00Z"), minValue = 5.0, avgValue = 5.0, maxValue = 5.0),
            seriesBucket(MetricResolution.RAW, at("2026-09-12T10:01:00Z"), minValue = 1.0, avgValue = 1.0, maxValue = 1.0),
            seriesBucket(MetricResolution.RAW, at("2026-09-12T10:02:00Z"), minValue = 9.0, avgValue = 9.0, maxValue = 9.0)
        )

        // When
        val merged = MetricSeriesBuckets.merge(buckets)

        // Then
        merged.samples shouldBe 3
        merged.minValue shouldBe 1.0
        merged.maxValue shouldBe 9.0
        merged.avgValue shouldBe 5.0
    }

    @Test
    fun `weights the average by the samples behind each part`() {
        // Given
        val buckets = listOf(
            seriesBucket(MetricResolution.FIVE_MINUTE, at("2026-09-12T10:00:00Z"), samples = 1, avgValue = 100.0),
            seriesBucket(MetricResolution.FIVE_MINUTE, at("2026-09-12T10:05:00Z"), samples = 9, avgValue = 0.0)
        )

        // When
        val merged = MetricSeriesBuckets.merge(buckets)

        // Then
        merged.samples shouldBe 10
        merged.avgValue shouldBe 10.0
    }

    @Test
    fun `takes the last value from the latest bucket rather than the largest`() {
        // Given
        val buckets = listOf(
            seriesBucket(MetricResolution.FIVE_MINUTE, at("2026-09-12T10:05:00Z"), lastValue = 7.0),
            seriesBucket(MetricResolution.FIVE_MINUTE, at("2026-09-12T10:00:00Z"), lastValue = 99.0)
        )

        // When
        val merged = MetricSeriesBuckets.merge(buckets)

        // Then
        merged.lastValue shouldBe 7.0
    }

    @Test
    fun `merges a single bucket to itself`() {
        // Given
        val bucket = rawBucket(at("2026-09-12T10:00:00Z"), value = 42.0)

        // When
        val merged = MetricSeriesBuckets.merge(listOf(bucket))

        // Then
        merged.samples shouldBe 1
        merged.minValue shouldBe 42.0
        merged.avgValue shouldBe 42.0
        merged.maxValue shouldBe 42.0
        merged.lastValue shouldBe 42.0
    }

    @Test
    fun `reaches the same answer through the tiers as straight from raw`() {
        // Given
        val raw = (0 until 180).map { minute ->
            rawBucket(at("2026-09-12T10:00:00Z").plusSeconds(minute * 60L), value = minute.toDouble())
        }

        // When
        val straightFromRaw = MetricSeriesBuckets.merge(raw)
        val fiveMinute = rollUp(raw, MetricResolution.FIVE_MINUTE)
        val hourly = rollUp(fiveMinute, MetricResolution.HOURLY)
        val throughTiers = MetricSeriesBuckets.merge(hourly)

        // Then
        fiveMinute.size shouldBe 36
        hourly.size shouldBe 3
        throughTiers.samples shouldBe straightFromRaw.samples
        throughTiers.minValue shouldBe straightFromRaw.minValue
        throughTiers.maxValue shouldBe straightFromRaw.maxValue
        throughTiers.avgValue.shouldBeWithinPercentageOf(straightFromRaw.avgValue, 0.0001)
        throughTiers.lastValue shouldBe straightFromRaw.lastValue
    }

    private fun rollUp(
        source: List<MetricSeriesBucketEntity>,
        target: MetricResolution
    ): List<MetricSeriesBucketEntity> = source
        .groupBy { MetricSeriesBuckets.startOf(it.bucketStart, target) }
        .toSortedMap()
        .map { (bucketStart, parts) ->
            val summary = MetricSeriesBuckets.merge(parts)
            seriesBucket(
                resolution = target,
                bucketStart = bucketStart,
                samples = summary.samples,
                minValue = summary.minValue,
                avgValue = summary.avgValue,
                maxValue = summary.maxValue,
                lastValue = summary.lastValue
            )
        }
}
