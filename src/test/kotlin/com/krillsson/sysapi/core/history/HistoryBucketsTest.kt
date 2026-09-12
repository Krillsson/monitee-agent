package com.krillsson.sysapi.core.history

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class HistoryBucketsTest {

    private val stockholm = ZoneId.of("Europe/Stockholm")

    @ParameterizedTest
    @CsvSource(
        "2026-09-12T10:00:00Z, 2026-09-12T10:00:00Z",
        "2026-09-12T10:04:59Z, 2026-09-12T10:00:00Z",
        "2026-09-12T10:05:00Z, 2026-09-12T10:05:00Z",
        "2026-09-12T10:59:59Z, 2026-09-12T10:55:00Z",
        "1969-12-31T23:57:30Z, 1969-12-31T23:55:00Z"
    )
    fun `floors an instant to its five minute boundary`(input: String, expected: String) {
        // Given
        val instant = Instant.parse(input)

        // When
        val start = HistoryBuckets.startOfFiveMinutes(instant)

        // Then
        start shouldBe Instant.parse(expected)
    }

    @Test
    fun `starts an hour bucket at the top of the hour`() {
        // Given
        val instant = Instant.parse("2026-09-12T10:37:12Z")

        // When
        val start = HistoryBuckets.startOfHour(instant, stockholm)

        // Then
        start shouldBe Instant.parse("2026-09-12T10:00:00Z")
    }

    @Test
    fun `starts a day bucket at local midnight`() {
        // Given
        val instant = Instant.parse("2026-09-12T10:37:12Z")

        // When
        val start = HistoryBuckets.startOfDay(instant, stockholm)

        // Then
        start shouldBe Instant.parse("2026-09-11T22:00:00Z")
    }

    @Test
    fun `ends a five minute bucket five minutes after it starts`() {
        // Given
        val start = Instant.parse("2026-09-12T10:05:00Z")

        // When
        val end = HistoryBuckets.endOfFiveMinutes(start)

        // Then
        end shouldBe Instant.parse("2026-09-12T10:10:00Z")
    }

    @Test
    fun `gives a spring forward day twenty three hours`() {
        // Given
        val start = HistoryBuckets.startOfDay(Instant.parse("2026-03-29T06:00:00Z"), stockholm)

        // When
        val end = HistoryBuckets.endOfDay(start, stockholm)

        // Then
        Duration.between(start, end) shouldBe Duration.ofHours(23)
    }

    @Test
    fun `gives an autumn back day twenty five hours`() {
        // Given
        val start = HistoryBuckets.startOfDay(Instant.parse("2026-10-25T06:00:00Z"), stockholm)

        // When
        val end = HistoryBuckets.endOfDay(start, stockholm)

        // Then
        Duration.between(start, end) shouldBe Duration.ofHours(25)
    }
}
