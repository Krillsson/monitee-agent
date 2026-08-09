package com.krillsson.sysapi.core.check

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.ZoneId

class CheckBucketsTest {

    private val stockholm = ZoneId.of("Europe/Stockholm")

    @ParameterizedTest
    @CsvSource(
        "UTC,          2026-08-01T10:45:00Z, 2026-08-01T10:00:00Z",
        "Asia/Kolkata, 2026-08-01T10:45:00Z, 2026-08-01T10:30:00Z"
    )
    fun `starts an hour bucket on the local hour boundary`(zoneId: String, timestamp: String, expected: String) {
        // Given
        val zone = ZoneId.of(zoneId)

        // When
        val bucketStart = CheckBuckets.startOfHour(at(timestamp), zone)

        // Then
        bucketStart shouldBe at(expected)
    }

    @Test
    fun `starts a day bucket at local midnight`() {
        // Given
        val timestamp = at("2026-08-01T10:45:00Z")

        // When
        val bucketStart = CheckBuckets.startOfDay(timestamp, stockholm)

        // Then
        bucketStart shouldBe at("2026-07-31T22:00:00Z")
    }

    @Test
    fun `ends a day bucket a real day later across a daylight saving change`() {
        // Given
        val springForward = CheckBuckets.startOfDay(at("2026-03-29T12:00:00Z"), stockholm)
        val autumnBack = CheckBuckets.startOfDay(at("2026-10-25T12:00:00Z"), stockholm)

        // When
        val springLength = Duration.between(springForward, CheckBuckets.endOf(springForward, BucketResolution.DAILY, stockholm))
        val autumnLength = Duration.between(autumnBack, CheckBuckets.endOf(autumnBack, BucketResolution.DAILY, stockholm))

        // Then
        springLength shouldBe Duration.ofHours(23)
        autumnLength shouldBe Duration.ofHours(25)
    }

    @Test
    fun `accumulates every outage in a bucket rather than only the last one`() {
        // Given
        val results = listOf(
            checkResult(at("2026-08-01T10:00:00Z")),
            checkResult(at("2026-08-01T10:01:00Z"), successful = false),
            checkResult(at("2026-08-01T10:02:00Z"), successful = false),
            checkResult(at("2026-08-01T10:03:00Z")),
            checkResult(at("2026-08-01T10:04:00Z")),
            checkResult(at("2026-08-01T10:05:00Z")),
            checkResult(at("2026-08-01T10:06:00Z"), successful = false),
            checkResult(at("2026-08-01T10:07:00Z")),
            checkResult(at("2026-08-01T10:08:00Z")),
            checkResult(at("2026-08-01T10:09:00Z"))
        )

        // When
        val summary = CheckBuckets.summarize(
            results,
            at("2026-08-01T10:00:00Z"),
            at("2026-08-01T11:00:00Z"),
            at("2026-08-01T12:00:00Z"),
            null
        )

        // Then
        summary.samples shouldBe 10
        summary.successful shouldBe 7
        summary.failed shouldBe 3
        summary.downtimeSeconds shouldBe 180
    }

    @Test
    fun `counts the seconds between the bucket start and its first sample when the check was already down`() {
        // Given
        val preceding = checkResult(at("2026-08-01T09:59:30Z"), successful = false)
        val results = listOf(
            checkResult(at("2026-08-01T10:00:30Z"), successful = false),
            checkResult(at("2026-08-01T10:01:30Z"))
        )

        // When
        val summary = CheckBuckets.summarize(
            results,
            at("2026-08-01T10:00:00Z"),
            at("2026-08-01T11:00:00Z"),
            at("2026-08-01T12:00:00Z"),
            preceding
        )

        // Then
        summary.downtimeSeconds shouldBe 90
    }

    @Test
    fun `counts a trailing outage up to the end of the bucket`() {
        // Given
        val results = listOf(
            checkResult(at("2026-08-01T10:00:00Z"), successful = false),
            checkResult(at("2026-08-01T10:01:00Z"), successful = false)
        )

        // When
        val summary = CheckBuckets.summarize(
            results,
            at("2026-08-01T10:00:00Z"),
            at("2026-08-01T11:00:00Z"),
            at("2026-08-01T12:00:00Z"),
            null
        )

        // Then
        summary.downtimeSeconds shouldBe 3600
    }

    @Test
    fun `stops a trailing outage at the current time when the bucket is still open`() {
        // Given
        val results = listOf(
            checkResult(at("2026-08-01T10:00:00Z"), successful = false),
            checkResult(at("2026-08-01T10:01:00Z"), successful = false)
        )

        // When
        val summary = CheckBuckets.summarize(
            results,
            at("2026-08-01T10:00:00Z"),
            at("2026-08-01T11:00:00Z"),
            at("2026-08-01T10:05:00Z"),
            null
        )

        // Then
        summary.downtimeSeconds shouldBe 300
    }

    @Test
    fun `summarises latency across the bucket`() {
        // Given
        val results = (1..10).map {
            checkResult(at("2026-08-01T10:00:00Z").plusSeconds(it * 60L), latencyMs = it * 10L)
        }

        // When
        val summary = CheckBuckets.summarize(
            results,
            at("2026-08-01T10:00:00Z"),
            at("2026-08-01T11:00:00Z"),
            at("2026-08-01T12:00:00Z"),
            null
        )

        // Then
        summary.minLatencyMs shouldBe 10
        summary.avgLatencyMs shouldBe 55
        summary.maxLatencyMs shouldBe 100
        summary.p95LatencyMs shouldBe 100
        summary.lastMessage shouldBe "OK"
    }

    @Test
    fun `merges buckets into a coarser one weighting the average by samples`() {
        // Given
        val buckets = listOf(
            bucket(
                BucketResolution.HOURLY,
                at("2026-08-01T10:00:00Z"),
                samples = 10,
                successful = 9,
                downtimeSeconds = 60,
                minLatencyMs = 10,
                avgLatencyMs = 50,
                maxLatencyMs = 100,
                p95LatencyMs = 90,
                lastMessage = "first"
            ),
            bucket(
                BucketResolution.HOURLY,
                at("2026-08-01T11:00:00Z"),
                samples = 20,
                successful = 20,
                downtimeSeconds = 0,
                minLatencyMs = 5,
                avgLatencyMs = 20,
                maxLatencyMs = 40,
                p95LatencyMs = 30,
                lastMessage = "last"
            )
        )

        // When
        val merged = CheckBuckets.merge(buckets)

        // Then
        merged.samples shouldBe 30
        merged.successful shouldBe 29
        merged.failed shouldBe 1
        merged.downtimeSeconds shouldBe 60
        merged.minLatencyMs shouldBe 5
        merged.avgLatencyMs shouldBe 30
        merged.maxLatencyMs shouldBe 100
        merged.p95LatencyMs shouldBe 90
        merged.lastMessage shouldBe "last"
    }
}
