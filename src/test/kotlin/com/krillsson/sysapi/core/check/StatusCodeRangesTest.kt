package com.krillsson.sysapi.core.check

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class StatusCodeRangesTest {

    @ParameterizedTest
    @CsvSource(
        "200-299, 200, true",
        "200-299, 299, true",
        "200-299, 300, false",
        "200-299, 199, false",
        "200, 200, true",
        "200, 201, false",
        "'200-299,301', 301, true",
        "'200-299,301', 302, false",
        "'200-299,301', 250, true",
        "100-599, 418, true",
        "404, 404, true"
    )
    fun `matches only the codes covered by the spec`(spec: String, code: Int, expected: Boolean) {
        // Given
        val ranges = StatusCodeRanges.parse(spec)

        // When
        val result = ranges.shouldNotBeNull().matches(code)

        // Then
        result shouldBe expected
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "nope", "200-", "300-200", "99", "600", "1-2-3"])
    fun `rejects a spec that is not codes or ranges`(spec: String) {
        // Given
        val given = spec

        // When
        val ranges = StatusCodeRanges.parse(given)

        // Then
        ranges shouldBe null
    }

    @Test
    fun `ignores whitespace around codes and ranges`() {
        // Given
        val spec = " 200 - 299 , 301 "

        // When
        val ranges = StatusCodeRanges.parse(spec)

        // Then
        ranges.shouldNotBeNull().matches(250) shouldBe true
        ranges.matches(301) shouldBe true
        ranges.matches(400) shouldBe false
    }
}
