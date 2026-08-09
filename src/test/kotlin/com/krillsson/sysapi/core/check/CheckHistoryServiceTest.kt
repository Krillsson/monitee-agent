package com.krillsson.sysapi.core.check

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant

class CheckHistoryServiceTest {

    private val resultRepository = mockk<CheckResultRepository>()
    private val bucketRepository = mockk<CheckResultBucketRepository>()
    private val service = CheckHistoryService(resultRepository, bucketRepository, configWithCheckRetention())

    @BeforeEach
    fun setUp() {
        every { resultRepository.findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(any(), any(), any()) } returns emptyList()
        every { bucketRepository.findByCheckIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(any(), any(), any(), any()) } returns emptyList()
    }

    @ParameterizedTest
    @CsvSource(
        "2,     0, RAW",
        "24,    0, HOURLY",
        "1,   120, HOURLY",
        "1440,  0, DAILY"
    )
    fun `picks a resolution the requested range can be drawn from`(
        rangeHours: Long,
        endsHoursAgo: Long,
        expected: CheckResolution
    ) {
        // Given
        val to = Instant.now().minus(Duration.ofHours(endsHoursAgo))
        val from = to.minus(Duration.ofHours(rangeHours))

        // When
        val history = service.history(CHECK_ID, from, to, CheckResolution.AUTO)

        // Then
        history.resolution shouldBe expected
    }

    @Test
    fun `serves the resolution that was asked for rather than the one it would have picked`() {
        // Given
        val to = Instant.now()
        val from = to.minus(Duration.ofHours(2))

        // When
        val history = service.history(CHECK_ID, from, to, CheckResolution.DAILY)

        // Then
        history.resolution shouldBe CheckResolution.DAILY
    }

    @Test
    fun `reports uptime as the share of the period the check was up`() {
        // Given
        val from = at("2026-08-01T00:00:00Z")
        val to = from.plus(Duration.ofDays(1))
        every {
            bucketRepository.findByCheckIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                CHECK_ID, BucketResolution.DAILY, any(), any()
            )
        } returns listOf(bucket(BucketResolution.DAILY, from, samples = 1440, successful = 1400, downtimeSeconds = 3600))

        // When
        val uptime = service.uptime(CHECK_ID, from, to)

        // Then
        uptime.downtimeSeconds shouldBe 3600
        uptime.totalSeconds shouldBe 86400
        uptime.uptimePercent shouldBe 95.8333
        uptime.samples shouldBe 1440
        uptime.failed shouldBe 40
    }

    @Test
    fun `keeps carrying downtime percent on the deprecated uptime metrics`() {
        // Given
        val dayStart = CheckBuckets.startOfDay(at("2026-08-01T12:00:00Z"))
        val dayEnd = CheckBuckets.endOf(dayStart, BucketResolution.DAILY)
        val daySeconds = Duration.between(dayStart, dayEnd).seconds
        every {
            bucketRepository.findByCheckIdAndResolutionAndBucketStartGreaterThanEqualAndBucketStartLessThanOrderByBucketStartAsc(
                CHECK_ID, BucketResolution.DAILY, any(), any()
            )
        } returns listOf(
            bucket(
                BucketResolution.DAILY,
                dayStart,
                samples = 1440,
                successful = 1400,
                downtimeSeconds = daySeconds / 100
            )
        )

        // When
        val metrics = service.uptimeMetrics(CHECK_ID, dayStart, dayEnd)

        // Then
        metrics.perDay shouldHaveSize 1
        metrics.perDay.first().uptimePercent shouldBe 1.0
        metrics.perDay.first().downTimeSeconds shouldBe daySeconds / 100
        metrics.total.totalUptimePercent shouldBe 1.0
    }

    @Test
    fun `keeps the most recent results when a limit is given`() {
        // Given
        val from = at("2026-08-01T10:00:00Z")
        val results = (0..4).map { checkResult(from.plusSeconds(it * 60L), message = "probe $it") }
        every {
            resultRepository.findByCheckIdAndTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(CHECK_ID, any(), any())
        } returns results

        // When
        val limited = service.resultsBetween(CHECK_ID, from, from.plus(Duration.ofHours(1)), 2)

        // Then
        limited shouldHaveSize 2
        limited.map { it.message } shouldBe listOf("probe 3", "probe 4")
    }
}
