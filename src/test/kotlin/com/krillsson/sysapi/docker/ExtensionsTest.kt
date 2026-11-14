package com.krillsson.sysapi.docker

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.dockerjava.api.model.MemoryStatsConfig
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionsTest {

    private val mapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun memoryStats(json: String): MemoryStatsConfig = mapper.readValue(json, MemoryStatsConfig::class.java)

    @Test
    fun `subtracts inactive_file from the usage on cgroup v2`() {
        // Given
        val stats = memoryStats(
            """{"usage": 600, "limit": 1000, "stats": {"anon": 350, "file": 250, "inactive_file": 200, "active_file": 50}}"""
        )

        // When
        val memoryUsage = stats.asMemoryUsage()

        // Then
        memoryUsage.usageBytes shouldBe 400
        memoryUsage.limitBytes shouldBe 1000
        memoryUsage.usagePercent shouldBe (40.0 plusOrMinus 0.0001)
    }

    @Test
    fun `subtracts total_inactive_file rather than inactive_file or cache on cgroup v1`() {
        // Given
        val stats = memoryStats(
            """{"usage": 800, "limit": 2000, "stats": {"cache": 500, "inactive_file": 100, "total_inactive_file": 300, "total_cache": 500}}"""
        )

        // When
        val memoryUsage = stats.asMemoryUsage()

        // Then
        memoryUsage.usageBytes shouldBe 500
        memoryUsage.usagePercent shouldBe (25.0 plusOrMinus 0.0001)
    }

    @Test
    fun `falls back to inactive_file on cgroup v1 when total_inactive_file is not smaller than the usage`() {
        // Given
        val stats = memoryStats(
            """{"usage": 800, "limit": 2000, "stats": {"inactive_file": 100, "total_inactive_file": 900}}"""
        )

        // When
        val memoryUsage = stats.asMemoryUsage()

        // Then
        memoryUsage.usageBytes shouldBe 700
    }

    @Test
    fun `keeps the whole usage when inactive_file is larger than the usage`() {
        // Given
        val stats = memoryStats("""{"usage": 600, "limit": 1000, "stats": {"inactive_file": 900}}""")

        // When
        val memoryUsage = stats.asMemoryUsage()

        // Then
        memoryUsage.usageBytes shouldBe 600
        memoryUsage.usagePercent shouldBe (60.0 plusOrMinus 0.0001)
    }

    @Test
    fun `keeps the whole usage when docker reports no inactive file pages`() {
        // Given
        val stats = memoryStats("""{"usage": 600, "limit": 1000}""")

        // When
        val memoryUsage = stats.asMemoryUsage()

        // Then
        memoryUsage.usageBytes shouldBe 600
        memoryUsage.usagePercent shouldBe (60.0 plusOrMinus 0.0001)
    }

    @Test
    fun `reports nothing used when docker sends no memory stats`() {
        // Given
        val stats = memoryStats("""{}""")

        // When
        val memoryUsage = stats.asMemoryUsage()

        // Then
        memoryUsage.usageBytes shouldBe 0
        memoryUsage.limitBytes shouldBe 0
        memoryUsage.usagePercent shouldBe 0.0
    }
}
