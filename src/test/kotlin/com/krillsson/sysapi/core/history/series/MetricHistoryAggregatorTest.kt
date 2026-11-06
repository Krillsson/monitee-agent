package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.config.RetentionConfiguration
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainOnlyNulls
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContainNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionOperations
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MetricHistoryAggregatorTest {

    private val repository = mockk<MetricSeriesBucketRepository>(relaxed = true)
    private val series = seriesKey(MetricId.CPU_USAGE_PERCENT)
    private val saved = mutableListOf<MetricSeriesBucketEntity>()
    private val transactions = RecordingTransactions()

    private val openFiveMinutes = MetricSeriesBuckets.startOf(Instant.now(), MetricResolution.FIVE_MINUTE)

    @BeforeEach
    fun setUp() {
        saved.clear()
        every { repository.findDistinctSeries(any()) } returns emptyList()
        every { repository.findByMetricAndItemIdAndResolutionAndBucketStartIn(any(), any(), any(), any()) } returns emptyList()
        every { repository.findFirstByMetricAndItemIdAndResolutionOrderByBucketStartDesc(any(), any(), any()) } returns Optional.empty()
        every { repository.findFirstByMetricAndItemIdAndResolutionOrderByBucketStartAsc(any(), any(), any()) } returns Optional.empty()
        every {
            repository.findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(), any(), any(), any(), any()
            )
        } returns emptyList()
        every { repository.saveAll(any<Iterable<MetricSeriesBucketEntity>>()) } answers {
            firstArg<Iterable<MetricSeriesBucketEntity>>().toMutableList().also { saved += it }
        }
    }

    private fun givenRawBuckets(buckets: List<MetricSeriesBucketEntity>) {
        every { repository.findDistinctSeries(MetricResolution.RAW) } returns listOf(series)
        every {
            repository.findFirstByMetricAndItemIdAndResolutionOrderByBucketStartAsc(
                series.metric,
                series.itemId,
                MetricResolution.RAW
            )
        } returns Optional.of(buckets.first())
        every {
            repository.findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                series.metric,
                series.itemId,
                MetricResolution.RAW,
                any(),
                any()
            )
        } answers {
            val from = arg<Instant>(3)
            val to = arg<Instant>(4)
            buckets.filter { !it.bucketStart.isBefore(from) && it.bucketStart.isBefore(to) }
        }
    }

    private fun aggregator() = MetricHistoryAggregator(repository, transactions, configWithSeriesRetention())

    @Test
    fun `merges closed five minute windows and leaves the one in progress alone`() {
        // Given
        val twoWindowsAgo = openFiveMinutes.minus(Duration.ofMinutes(10))
        val oneWindowAgo = openFiveMinutes.minus(Duration.ofMinutes(5))
        givenRawBuckets(
            listOf(
                rawBucket(twoWindowsAgo, value = 10.0),
                rawBucket(twoWindowsAgo.plusSeconds(60), value = 30.0),
                rawBucket(oneWindowAgo, value = 50.0),
                rawBucket(openFiveMinutes.plusSeconds(30), value = 90.0)
            )
        )

        // When
        aggregator().rollUpExistingHistory()

        // Then
        val fiveMinute = saved.filter { it.resolution == MetricResolution.FIVE_MINUTE }
        fiveMinute shouldHaveSize 2
        fiveMinute.map { it.bucketStart } shouldBe listOf(twoWindowsAgo, oneWindowAgo)
        fiveMinute.first().samples shouldBe 2
        fiveMinute.first().minValue shouldBe 10.0
        fiveMinute.first().maxValue shouldBe 30.0
        fiveMinute.first().avgValue shouldBe 20.0
        fiveMinute.first().lastValue shouldBe 30.0
    }

    @Test
    fun `carries a spike into the bucket maximum`() {
        // Given
        val window = openFiveMinutes.minus(Duration.ofMinutes(5))
        givenRawBuckets(
            listOf(
                rawBucket(window, value = 2.0),
                rawBucket(window.plusSeconds(60), value = 99.0),
                rawBucket(window.plusSeconds(120), value = 3.0)
            )
        )

        // When
        aggregator().rollUpExistingHistory()

        // Then
        val bucket = saved.single { it.resolution == MetricResolution.FIVE_MINUTE }
        bucket.maxValue shouldBe 99.0
        bucket.minValue shouldBe 2.0
    }

    @Test
    fun `resumes from the end of the newest stored bucket`() {
        // Given
        val existing = seriesBucket(MetricResolution.FIVE_MINUTE, openFiveMinutes.minus(Duration.ofMinutes(10)))
        every {
            repository.findFirstByMetricAndItemIdAndResolutionOrderByBucketStartDesc(
                series.metric,
                series.itemId,
                MetricResolution.FIVE_MINUTE
            )
        } returns Optional.of(existing)
        givenRawBuckets(
            listOf(
                rawBucket(openFiveMinutes.minus(Duration.ofMinutes(10)), value = 1.0),
                rawBucket(openFiveMinutes.minus(Duration.ofMinutes(5)), value = 2.0)
            )
        )

        // When
        aggregator().rollUpExistingHistory()

        // Then
        val fiveMinute = saved.filter { it.resolution == MetricResolution.FIVE_MINUTE }
        fiveMinute shouldHaveSize 1
        fiveMinute.single().bucketStart shouldBe openFiveMinutes.minus(Duration.ofMinutes(5))
    }

    @Test
    fun `reuses the existing row id so a repeated pass updates rather than duplicates`() {
        // Given
        val window = openFiveMinutes.minus(Duration.ofMinutes(5))
        val existing = seriesBucket(MetricResolution.FIVE_MINUTE, window)
        every {
            repository.findByMetricAndItemIdAndResolutionAndBucketStartIn(
                series.metric,
                series.itemId,
                MetricResolution.FIVE_MINUTE,
                any()
            )
        } returns listOf(existing)
        givenRawBuckets(listOf(rawBucket(window, value = 7.0)))

        // When
        aggregator().rollUpExistingHistory()

        // Then
        saved.single { it.resolution == MetricResolution.FIVE_MINUTE }.id shouldBe existing.id
    }

    @Test
    fun `writes nothing when the only raw data is still inside the open window`() {
        // Given
        givenRawBuckets(listOf(rawBucket(openFiveMinutes.plusSeconds(10), value = 5.0)))

        // When
        aggregator().rollUpExistingHistory()

        // Then
        saved.shouldBeEmpty()
    }

    @Test
    fun `purges every tier against its own retention`() {
        // Given
        val aggregator = MetricHistoryAggregator(
            repository,
            transactions,
            configWithSeriesRetention(
                raw = RetentionConfiguration(12, ChronoUnit.HOURS),
                fiveMinute = RetentionConfiguration(72, ChronoUnit.HOURS),
                hourly = RetentionConfiguration(90, ChronoUnit.DAYS),
                daily = RetentionConfiguration(730, ChronoUnit.DAYS)
            )
        )

        // When
        aggregator.rollUpClosedPeriods()

        // Then
        verify { repository.deleteOlderThan(MetricResolution.RAW, any()) }
        verify { repository.deleteOlderThan(MetricResolution.FIVE_MINUTE, any()) }
        verify { repository.deleteOlderThan(MetricResolution.HOURLY, any()) }
        verify { repository.deleteOlderThan(MetricResolution.DAILY, any()) }
    }

    @Test
    fun `merges five minute buckets into hours and hours into days`() {
        // Given
        val hour = MetricSeriesBuckets.startOf(Instant.now(), MetricResolution.HOURLY).minus(Duration.ofHours(2))
        every { repository.findDistinctSeries(MetricResolution.FIVE_MINUTE) } returns listOf(series)
        every {
            repository.findFirstByMetricAndItemIdAndResolutionOrderByBucketStartAsc(
                series.metric,
                series.itemId,
                MetricResolution.FIVE_MINUTE
            )
        } returns Optional.of(seriesBucket(MetricResolution.FIVE_MINUTE, hour))
        every {
            repository.findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                series.metric,
                series.itemId,
                MetricResolution.FIVE_MINUTE,
                any(),
                any()
            )
        } returns listOf(
            seriesBucket(MetricResolution.FIVE_MINUTE, hour, samples = 5, minValue = 1.0, avgValue = 4.0, maxValue = 8.0),
            seriesBucket(
                MetricResolution.FIVE_MINUTE,
                hour.plus(Duration.ofMinutes(5)),
                samples = 5,
                minValue = 3.0,
                avgValue = 6.0,
                maxValue = 20.0
            )
        )

        // When
        aggregator().rollUpExistingHistory()

        // Then
        val hourly = saved.single { it.resolution == MetricResolution.HOURLY }
        hourly.bucketStart shouldBe hour
        hourly.samples shouldBe 10
        hourly.minValue shouldBe 1.0
        hourly.maxValue shouldBe 20.0
        hourly.avgValue shouldBe 5.0
    }

    @Test
    fun `rolls up each series in a transaction of its own`() {
        // Given
        val window = openFiveMinutes.minus(Duration.ofMinutes(5))
        val cpu = seriesKey(MetricId.CPU_USAGE_PERCENT)
        val memory = seriesKey(MetricId.MEMORY_USED_PERCENT)
        val seriesLookedUpIn = mutableListOf<Int?>()
        every { repository.findDistinctSeries(any()) } answers {
            seriesLookedUpIn += transactions.current
            if (firstArg<MetricResolution>() == MetricResolution.RAW) listOf(cpu, memory) else emptyList()
        }
        every {
            repository.findFirstByMetricAndItemIdAndResolutionOrderByBucketStartAsc(any(), any(), MetricResolution.RAW)
        } answers { Optional.of(rawBucket(window, value = 1.0)) }
        every {
            repository.findByMetricAndItemIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                any(), any(), MetricResolution.RAW, any(), any()
            )
        } answers { listOf(rawBucket(window, value = 1.0)) }
        val savedIn = mutableListOf<Int?>()
        every { repository.saveAll(any<Iterable<MetricSeriesBucketEntity>>()) } answers {
            savedIn += transactions.current
            firstArg<Iterable<MetricSeriesBucketEntity>>().toMutableList()
        }

        // When
        aggregator().rollUpClosedPeriods()

        // Then
        seriesLookedUpIn.shouldContainOnlyNulls()
        savedIn shouldHaveSize 2
        savedIn.shouldNotContainNull()
        savedIn.distinct() shouldHaveSize 2
    }

    @Test
    fun `purges each tier in a transaction of its own`() {
        // Given
        val purgedIn = mutableListOf<Int?>()
        every { repository.deleteOlderThan(any(), any()) } answers {
            purgedIn += transactions.current
            0
        }

        // When
        aggregator().rollUpClosedPeriods()

        // Then
        purgedIn shouldHaveSize 4
        purgedIn.shouldNotContainNull()
        purgedIn.distinct() shouldHaveSize 4
    }

    @Test
    fun `waits for a roll-up in progress before starting another`() {
        // Given
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val lookups = AtomicInteger()
        every { repository.findDistinctSeries(any()) } answers {
            if (lookups.incrementAndGet() == 1) {
                firstStarted.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
            }
            emptyList()
        }
        val aggregator = aggregator()
        val first = thread { aggregator.rollUpExistingHistory() }
        firstStarted.await(5, TimeUnit.SECONDS) shouldBe true

        // When
        val second = thread { aggregator.rollUpClosedPeriods() }
        second.join(300)

        // Then
        lookups.get() shouldBe 1
        releaseFirst.countDown()
        first.join(5_000)
        second.join(5_000)
        lookups.get() shouldBe 6
    }

    private class RecordingTransactions : TransactionOperations {
        private var started = 0
        var current: Int? = null
            private set

        override fun <T> execute(action: TransactionCallback<T>): T? {
            check(current == null) { "transactions must not nest" }
            current = ++started
            try {
                return action.doInTransaction(SimpleTransactionStatus())
            } finally {
                current = null
            }
        }
    }
}
