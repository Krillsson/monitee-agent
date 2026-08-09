package com.krillsson.sysapi.core.check

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Optional

class CheckHistoryAggregatorTest {

    private val resultRepository = mockk<CheckResultRepository>(relaxed = true)
    private val bucketRepository = mockk<CheckResultBucketRepository>(relaxed = true)
    private val aggregator = CheckHistoryAggregator(resultRepository, bucketRepository, configWithCheckRetention())

    private val openHour = CheckBuckets.startOfHour(Instant.now())
    private val savedBuckets = mutableListOf<CheckResultBucketEntity>()

    @BeforeEach
    fun setUp() {
        savedBuckets.clear()
        every { resultRepository.findDistinctCheckIds() } returns listOf(CHECK_ID)
        every { bucketRepository.findDistinctCheckIds(any()) } returns emptyList()
        every { bucketRepository.findByCheckIdAndResolutionAndBucketStartIn(any(), any(), any()) } returns emptyList()
        every { resultRepository.findFirstByCheckIdAndTimestampLessThanOrderByTimestampDesc(any(), any()) } returns Optional.empty()
        every { bucketRepository.findFirstByCheckIdAndResolutionOrderByBucketStartDesc(any(), any()) } returns Optional.empty()
        every { bucketRepository.saveAll(any<Iterable<CheckResultBucketEntity>>()) } answers {
            firstArg<Iterable<CheckResultBucketEntity>>().toMutableList().also { savedBuckets += it }
        }
    }

    private fun givenRawResults(results: List<CheckResultEntity>) {
        every { resultRepository.findFirstByCheckIdOrderByTimestampAsc(CHECK_ID) } returns Optional.of(results.first())
        every {
            resultRepository.findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(CHECK_ID, any(), any())
        } answers {
            val from = secondArg<Instant>()
            val to = thirdArg<Instant>()
            results.filter { !it.timestamp.isBefore(from) && it.timestamp.isBefore(to) }
        }
    }

    @Test
    fun `rolls closed hours into buckets and leaves the hour in progress alone`() {
        // Given
        val twoHoursAgo = openHour.minus(Duration.ofHours(2))
        val oneHourAgo = openHour.minus(Duration.ofHours(1))
        givenRawResults(
            listOf(
                checkResult(twoHoursAgo, latencyMs = 10),
                checkResult(twoHoursAgo.plusSeconds(1800), successful = false, latencyMs = 90),
                checkResult(oneHourAgo, latencyMs = 50),
                checkResult(openHour.plusSeconds(60), latencyMs = 70)
            )
        )

        // When
        aggregator.rollUpExistingHistory()

        // Then
        savedBuckets shouldHaveSize 2
        savedBuckets.map { it.bucketStart } shouldBe listOf(twoHoursAgo, oneHourAgo)
        savedBuckets.first().samples shouldBe 2
        savedBuckets.first().failed shouldBe 1
        savedBuckets.first().downtimeSeconds shouldBe 1800
        savedBuckets.last().samples shouldBe 1
    }

    @Test
    fun `carries an outage over a bucket boundary into the next bucket`() {
        // Given
        val oneHourAgo = openHour.minus(Duration.ofHours(1))
        every { resultRepository.findFirstByCheckIdAndTimestampLessThanOrderByTimestampDesc(CHECK_ID, any()) } returns
            Optional.of(checkResult(oneHourAgo.minusSeconds(30), successful = false))
        givenRawResults(listOf(checkResult(oneHourAgo.plusSeconds(30))))

        // When
        aggregator.rollUpExistingHistory()

        // Then
        savedBuckets shouldHaveSize 1
        savedBuckets.first().downtimeSeconds shouldBe 30
    }

    @Test
    fun `starts from the newest stored bucket rather than the oldest raw result`() {
        // Given
        val oneHourAgo = openHour.minus(Duration.ofHours(1))
        every { bucketRepository.findFirstByCheckIdAndResolutionOrderByBucketStartDesc(CHECK_ID, BucketResolution.HOURLY) } returns
            Optional.of(bucket(BucketResolution.HOURLY, openHour.minus(Duration.ofHours(2)), samples = 60, successful = 60))
        givenRawResults(listOf(checkResult(openHour.minus(Duration.ofHours(5))), checkResult(oneHourAgo)))
        val from = slot<Instant>()

        // When
        aggregator.rollUpExistingHistory()

        // Then
        verify {
            resultRepository.findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
                CHECK_ID,
                capture(from),
                any()
            )
        }
        from.captured shouldBe oneHourAgo
        savedBuckets shouldHaveSize 1
    }

    @Test
    fun `purges each tier on its own retention`() {
        // Given
        givenRawResults(listOf(checkResult(openHour.minus(Duration.ofHours(1)))))

        // When
        aggregator.rollUpExistingHistory()

        // Then
        verify { resultRepository.deleteOlderThan(within(Instant.now().minus(Duration.ofHours(48)))) }
        verify {
            bucketRepository.deleteOlderThan(
                BucketResolution.HOURLY,
                within(CheckBuckets.startOfHour(Instant.now().minus(Duration.ofDays(90))))
            )
        }
        verify {
            bucketRepository.deleteOlderThan(
                BucketResolution.DAILY,
                within(CheckBuckets.startOfDay(Instant.now().minus(Duration.ofDays(730))))
            )
        }
    }

    private fun MockKMatcherScope.within(expected: Instant) = match<Instant> {
        Duration.between(it, expected).abs() < Duration.ofMinutes(1)
    }
}
