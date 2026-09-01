package com.krillsson.sysapi.core.forecast

import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecastHistoryPoint
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class FileSystemSpaceForecastHistoryJsonConverterTest {

    private val converter = FileSystemSpaceForecastHistoryJsonConverter()

    @Test
    fun `round-trips a list of history points, including the instant precision`() {
        // Given
        val points = listOf(
            FileSystemSpaceForecastHistoryPoint(Instant.parse("2026-01-01T00:00:00.123Z"), 10_000),
            FileSystemSpaceForecastHistoryPoint(Instant.parse("2026-01-02T00:00:00.456Z"), 11_000)
        )

        // When
        val columnValue = converter.convertToDatabaseColumn(points)
        val roundTripped = converter.convertToEntityAttribute(columnValue)

        // Then
        roundTripped shouldBe points
    }

    @Test
    fun `converts null in both directions`() {
        converter.convertToDatabaseColumn(null).shouldBeNull()
        converter.convertToEntityAttribute(null).shouldBeNull()
    }
}
