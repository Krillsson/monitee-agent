package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.config.RetentionConfiguration
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class MetricHistoryServiceTest {

    private val repository = mockk<MetricSeriesBucketRepository>(relaxed = true)
    private val metric = MetricId.CPU_USAGE_PERCENT
    private val itemId = MetricId.HOST_WIDE
    private val stored = mutableMapOf<MetricResolution, List<MetricSeriesBucketEntity>>()

    private val service = MetricHistoryService(repository, configWithSeriesRetention())

    @BeforeEach
    fun setUp() {
        stored.clear()
        every {
            repository.findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                metric, itemId, any(), any(), any()
            )
        } answers {
            val resolution = arg<MetricResolution>(2)
            val from = arg<Instant>(3)
            val to = arg<Instant>(4)
            stored[resolution].orEmpty()
                .filter { !it.bucketStart.isBefore(from) && it.bucketStart.isBefore(to) }
                .sortedBy { it.bucketStart }
        }
    }

    @Test
    fun `picks raw for a short recent range`() {
        // Given
        val to = Instant.now()

        // When
        val resolution = service.resolutionFor(to.minus(Duration.ofHours(4)), to)

        // Then
        resolution shouldBe MetricResolution.RAW
    }

    @Test
    fun `picks five minute buckets for a day rather than hourly`() {
        // Given
        val to = Instant.now()

        // When
        val resolution = service.resolutionFor(to.minus(Duration.ofHours(24)), to)

        // Then
        resolution shouldBe MetricResolution.FIVE_MINUTE
    }

    @Test
    fun `picks hourly for a week and daily for a year`() {
        // Given
        val to = Instant.now()

        // When
        val week = service.resolutionFor(to.minus(Duration.ofDays(7)), to)
        val year = service.resolutionFor(to.minus(Duration.ofDays(365)), to)

        // Then
        week shouldBe MetricResolution.HOURLY
        year shouldBe MetricResolution.DAILY
    }

    @Test
    fun `falls past raw for a short range that starts before the raw window`() {
        // Given
        val service = MetricHistoryService(
            repository,
            configWithSeriesRetention(raw = RetentionConfiguration(12, ChronoUnit.HOURS))
        )
        val to = Instant.now().minus(Duration.ofHours(20))

        // When
        val resolution = service.resolutionFor(to.minus(Duration.ofHours(2)), to)

        // Then
        resolution shouldBe MetricResolution.FIVE_MINUTE
    }

    @Test
    fun `reports the resolution it actually served`() {
        // Given
        val to = Instant.now()

        // When
        val history = service.history(metric, itemId, to.minus(Duration.ofDays(365)), to, null)

        // Then
        history.resolution shouldBe HistoryResolution.DAILY
        history.points.shouldBeEmpty()
    }

    @Test
    fun `honours an explicit resolution over the range`() {
        // Given
        val to = Instant.now()
        stored[MetricResolution.DAILY] = listOf(
            seriesBucket(MetricResolution.DAILY, to.minus(Duration.ofDays(1)), samples = 288)
        )

        // When
        val history = service.history(metric, itemId, to.minus(Duration.ofHours(2)), to, HistoryResolution.DAILY)

        // Then
        history.resolution shouldBe HistoryResolution.DAILY
    }

    @Test
    fun `prefers the coarsest stored tier and fills the remainder from finer data`() {
        // Given
        val hourStart = MetricSeriesBuckets.startOf(Instant.now(), MetricResolution.HOURLY)
        val from = hourStart.minus(Duration.ofHours(2))
        stored[MetricResolution.HOURLY] = listOf(
            seriesBucket(MetricResolution.HOURLY, from, samples = 60, minValue = 1.0, avgValue = 2.0, maxValue = 3.0)
        )
        stored[MetricResolution.FIVE_MINUTE] = listOf(
            seriesBucket(
                MetricResolution.FIVE_MINUTE,
                from.plus(Duration.ofHours(1)),
                samples = 5,
                minValue = 10.0,
                avgValue = 20.0,
                maxValue = 30.0
            )
        )

        // When
        val points = service.tieredPoints(metric, itemId, from, hourStart)

        // Then
        points shouldHaveSize 2
        points.first().samples shouldBe 60
        points.last().samples shouldBe 5
        points.last().max shouldBe 30.0
    }

    @Test
    fun `summarises the bucket still in progress from finer data`() {
        // Given
        val hourStart = MetricSeriesBuckets.startOf(Instant.now(), MetricResolution.HOURLY)
        val to = hourStart.plus(Duration.ofMinutes(30))
        stored[MetricResolution.FIVE_MINUTE] = listOf(
            seriesBucket(
                MetricResolution.FIVE_MINUTE,
                hourStart,
                samples = 5,
                minValue = 4.0,
                avgValue = 6.0,
                maxValue = 8.0,
                lastValue = 5.0
            ),
            seriesBucket(
                MetricResolution.FIVE_MINUTE,
                hourStart.plus(Duration.ofMinutes(5)),
                samples = 5,
                minValue = 1.0,
                avgValue = 2.0,
                maxValue = 20.0,
                lastValue = 7.0
            )
        )

        // When
        val history = service.history(metric, itemId, hourStart, to, HistoryResolution.HOURLY)

        // Then
        history.points shouldHaveSize 1
        val point = history.points.single()
        point.timestamp shouldBe hourStart
        point.samples shouldBe 10
        point.min shouldBe 1.0
        point.max shouldBe 20.0
        point.avg shouldBe 4.0
        point.last shouldBe 7.0
    }

    @Test
    fun `returns nothing for an inverted range`() {
        // Given
        val now = Instant.now()

        // When
        val history = service.history(metric, itemId, now, now.minus(Duration.ofHours(1)), HistoryResolution.RAW)

        // Then
        history.points.shouldBeEmpty()
    }
}
