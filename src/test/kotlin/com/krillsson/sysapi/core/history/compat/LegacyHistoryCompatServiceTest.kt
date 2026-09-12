package com.krillsson.sysapi.core.history.compat

import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.db.BasicHistorySystemLoadEntity
import com.krillsson.sysapi.core.history.series.MetricId
import com.krillsson.sysapi.core.history.series.MetricResolution
import com.krillsson.sysapi.core.history.series.MetricSeriesBuckets
import com.krillsson.sysapi.core.history.series.MetricSeriesBucketEntity
import com.krillsson.sysapi.core.history.series.MetricSeriesBucketRepository
import com.krillsson.sysapi.core.history.series.seriesBucket
import com.krillsson.sysapi.docker.ContainersHistoryRepository
import com.krillsson.sysapi.ups.UpsMetricsHistoryRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class LegacyHistoryCompatServiceTest {

    private val historyRepository = mockk<HistoryRepository>(relaxed = true)
    private val containersHistoryRepository = mockk<ContainersHistoryRepository>(relaxed = true)
    private val upsMetricsHistoryRepository = mockk<UpsMetricsHistoryRepository>(relaxed = true)
    private val seriesRepository = mockk<MetricSeriesBucketRepository>(relaxed = true)

    private val service = LegacyHistoryCompatService(
        historyRepository,
        containersHistoryRepository,
        upsMetricsHistoryRepository,
        seriesRepository
    )

    private val hourStart = MetricSeriesBuckets.startOf(Instant.now(), MetricResolution.HOURLY)

    @BeforeEach
    fun setUp() {
        every { historyRepository.getHistoryLimitedToDates(any(), any()) } returns emptyList()
        every { containersHistoryRepository.getHistoryLimitedToDates(any(), any(), any()) } returns emptyList()
        every { upsMetricsHistoryRepository.getHistoryLimitedToDates(any(), any(), any()) } returns emptyList()
        every {
            seriesRepository.findByResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(), any(), any()
            )
        } returns emptyList()
    }

    private fun givenSeries(resolution: MetricResolution, buckets: List<MetricSeriesBucketEntity>) {
        every {
            seriesRepository.findByResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                resolution, any(), any()
            )
        } answers {
            val from = arg<Instant>(1)
            val to = arg<Instant>(2)
            buckets.filter { !it.bucketStart.isBefore(from) && it.bucketStart.isBefore(to) }
        }
    }

    @Test
    fun `serves recorded rows as stored points`() {
        // Given
        val entity = storedEntity(hourStart)
        every { historyRepository.getHistoryLimitedToDates(any(), any()) } returns listOf(entity)

        // When
        val points = service.systemHistory(hourStart.minus(Duration.ofHours(1)), hourStart.plusSeconds(60))

        // Then
        points shouldHaveSize 1
        points.single().shouldBeInstanceOf<SystemHistoryPoint.Stored>()
    }

    @Test
    fun `synthesizes only the part of the range the recorded rows do not cover`() {
        // Given
        val from = hourStart.minus(Duration.ofDays(3))
        val entity = storedEntity(hourStart)
        every { historyRepository.getHistoryLimitedToDates(any(), any()) } returns listOf(entity)
        givenSeries(
            MetricResolution.HOURLY,
            listOf(
                seriesBucket(MetricResolution.HOURLY, from, metric = MetricId.CPU_USAGE_PERCENT),
                seriesBucket(MetricResolution.HOURLY, from.plus(Duration.ofHours(1)), metric = MetricId.CPU_USAGE_PERCENT),
                seriesBucket(MetricResolution.HOURLY, hourStart.plus(Duration.ofHours(1)), metric = MetricId.CPU_USAGE_PERCENT)
            )
        )

        // When
        val points = service.systemHistory(from, hourStart.plusSeconds(60))

        // Then
        points shouldHaveSize 3
        points.take(2).forEach { it.shouldBeInstanceOf<SystemHistoryPoint.Synthesized>() }
        points.last().shouldBeInstanceOf<SystemHistoryPoint.Stored>()
    }

    @Test
    fun `orders synthesized points before the recorded ones`() {
        // Given
        val from = hourStart.minus(Duration.ofDays(3))
        every { historyRepository.getHistoryLimitedToDates(any(), any()) } returns listOf(storedEntity(hourStart))
        givenSeries(
            MetricResolution.HOURLY,
            listOf(seriesBucket(MetricResolution.HOURLY, from, metric = MetricId.CPU_USAGE_PERCENT))
        )

        // When
        val points = service.systemHistory(from, hourStart.plusSeconds(60))

        // Then
        points.map { it.timestamp } shouldBe listOf(from, hourStart)
    }

    @Test
    fun `synthesizes nothing when the recorded rows already cover the range`() {
        // Given
        val from = hourStart.minus(Duration.ofMinutes(30))
        every { historyRepository.getHistoryLimitedToDates(any(), any()) } returns listOf(storedEntity(from))
        givenSeries(
            MetricResolution.FIVE_MINUTE,
            listOf(seriesBucket(MetricResolution.FIVE_MINUTE, from.minus(Duration.ofHours(2))))
        )

        // When
        val points = service.systemHistory(from, hourStart)

        // Then
        points shouldHaveSize 1
        points.single().shouldBeInstanceOf<SystemHistoryPoint.Stored>()
    }

    @Test
    fun `never synthesizes from raw, since the wide table already covers that window`() {
        // Given
        val to = Instant.now()
        val from = to.minus(Duration.ofHours(2))
        givenSeries(
            MetricResolution.FIVE_MINUTE,
            listOf(seriesBucket(MetricResolution.FIVE_MINUTE, from, metric = MetricId.CPU_USAGE_PERCENT))
        )
        givenSeries(MetricResolution.RAW, listOf(seriesBucket(MetricResolution.RAW, from)))

        // When
        val points = service.systemHistory(from, to)

        // Then
        points shouldHaveSize 1
        points.single().timestamp shouldBe from
    }

    @Test
    fun `treats a bucket without the running flag as a container gap`() {
        // Given
        val from = hourStart.minus(Duration.ofDays(3))
        givenSeries(
            MetricResolution.HOURLY,
            listOf(
                seriesBucket(
                    MetricResolution.HOURLY,
                    from,
                    minValue = 1.0,
                    metric = MetricId.CONTAINER_RUNNING,
                    itemId = "abc"
                ),
                seriesBucket(
                    MetricResolution.HOURLY,
                    from.plus(Duration.ofHours(1)),
                    metric = MetricId.CONTAINER_CPU_PERCENT,
                    itemId = "abc"
                )
            )
        )

        // When
        val entries = service.containerHistory("abc", from, hourStart)

        // Then
        entries shouldHaveSize 1
        entries.single().timestamp shouldBe from
    }

    @Test
    fun `reports a ups as operating normally only when the flag held`() {
        // Given
        val from = hourStart.minus(Duration.ofDays(3))
        givenSeries(
            MetricResolution.HOURLY,
            listOf(
                seriesBucket(
                    MetricResolution.HOURLY,
                    from,
                    minValue = 1.0,
                    metric = MetricId.UPS_OPERATING_NORMALLY,
                    itemId = "ups-1"
                ),
                seriesBucket(
                    MetricResolution.HOURLY,
                    from.plus(Duration.ofHours(1)),
                    minValue = 0.0,
                    metric = MetricId.UPS_OPERATING_NORMALLY,
                    itemId = "ups-1"
                )
            )
        )

        // When
        val entries = service.upsHistory("ups-1", from, hourStart)

        // Then
        entries shouldHaveSize 2
        entries.first().metrics.isOperatingNormally() shouldBe true
        entries.last().metrics.isOperatingNormally() shouldBe false
    }

    @Test
    fun `falls to a finer tier when the coarser one holds nothing for the range`() {
        // Given
        val from = hourStart.minus(Duration.ofDays(5))
        givenSeries(
            MetricResolution.FIVE_MINUTE,
            listOf(seriesBucket(MetricResolution.FIVE_MINUTE, from, metric = MetricId.CPU_USAGE_PERCENT))
        )

        // When
        val points = service.systemHistory(from, hourStart)

        // Then
        points shouldHaveSize 1
        points.single().timestamp shouldBe from
    }

    @Test
    fun `prefers the coarser tier and only fills what it leaves uncovered`() {
        // Given
        val dayOne = MetricSeriesBuckets.startOf(hourStart, MetricResolution.DAILY).minus(Duration.ofDays(2))
        val dayTwo = MetricSeriesBuckets.endOf(dayOne, MetricResolution.DAILY)
        val insideDayOne = dayOne.plus(Duration.ofHours(6))
        val insideDayTwo = dayTwo.plus(Duration.ofHours(6))
        givenSeries(
            MetricResolution.DAILY,
            listOf(seriesBucket(MetricResolution.DAILY, dayOne, metric = MetricId.CPU_USAGE_PERCENT))
        )
        givenSeries(
            MetricResolution.FIVE_MINUTE,
            listOf(
                seriesBucket(MetricResolution.FIVE_MINUTE, insideDayOne, metric = MetricId.CPU_USAGE_PERCENT),
                seriesBucket(MetricResolution.FIVE_MINUTE, insideDayTwo, metric = MetricId.CPU_USAGE_PERCENT)
            )
        )

        // When
        val points = service.systemHistory(dayOne, hourStart)

        // Then
        points.map { it.timestamp } shouldBe listOf(dayOne, insideDayTwo)
    }

    @Test
    fun `returns nothing for an inverted range`() {
        // Given
        val now = Instant.now()

        // When
        val points = service.systemHistory(now, now.minus(Duration.ofHours(1)))

        // Then
        points.shouldBeEmpty()
    }

    private fun storedEntity(date: Instant) = BasicHistorySystemLoadEntity(
        id = UUID.randomUUID(),
        date = date,
        uptime = 100,
        systemLoadAverage = 1.0
    )
}
