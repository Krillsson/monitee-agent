package com.krillsson.sysapi.core.history.series

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException

class MetricSeriesBucketMigrationTest {

    @TempDir
    lateinit var directory: Path

    private val url by lazy { "jdbc:sqlite:${directory.resolve("database.sqlite")}" }

    private fun migrate(target: String? = null) {
        Flyway.configure()
            .dataSource(url, null, null)
            .locations("classpath:db/migration")
            .apply { target?.let { target(it) } }
            .load()
            .migrate()
    }

    private fun insertBucket(id: String, bucketStart: Long = 1_000) {
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().executeUpdate(
                """
                INSERT INTO MetricSeriesBucketEntity
                    (id, metric, itemId, resolution, bucketStart, samples, minValue, avgValue, maxValue, lastValue)
                VALUES ('$id', 'CPU_USAGE_PERCENT', '', 'RAW', $bucketStart, 1, 1.0, 1.0, 1.0, 1.0)
                """.trimIndent()
            )
        }
    }

    private fun queryPlan(sql: String): String = DriverManager.getConnection(url).use { connection ->
        connection.createStatement().executeQuery("EXPLAIN QUERY PLAN $sql").use { rows ->
            generateSequence { if (rows.next()) rows.getString("detail") else null }.joinToString("\n")
        }
    }

    @Test
    fun `keeps the buckets of an existing database`() {
        // Given
        migrate(target = "21")
        insertBucket("bucket")

        // When
        migrate()

        // Then
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().executeQuery("SELECT count(*) FROM MetricSeriesBucketEntity").use { row ->
                row.next() shouldBe true
                row.getInt(1) shouldBe 1
            }
        }
    }

    @Test
    fun `still rejects a second bucket for the same series, resolution and start`() {
        // Given
        migrate()
        insertBucket("first")

        // When
        val error = shouldThrow<SQLException> { insertBucket("second") }

        // Then
        error.message shouldContain "UNIQUE constraint failed"
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "SELECT * FROM MetricSeriesBucketEntity WHERE resolution = 'HOURLY' AND bucketStart >= 0 AND bucketStart < 1 ORDER BY bucketStart",
            "SELECT DISTINCT metric, itemId FROM MetricSeriesBucketEntity WHERE resolution = 'RAW'",
            "DELETE FROM MetricSeriesBucketEntity WHERE resolution = 'RAW' AND bucketStart < 1",
            "SELECT * FROM MetricSeriesBucketEntity WHERE resolution = 'DAILY' ORDER BY bucketStart DESC LIMIT 1",
            "SELECT * FROM MetricSeriesBucketEntity WHERE metric = 'CPU_USAGE_PERCENT' AND itemId = '' AND resolution = 'RAW' AND bucketStart >= 0 AND bucketStart < 1 ORDER BY bucketStart",
            "SELECT * FROM MetricSeriesBucketEntity WHERE metric = 'CPU_USAGE_PERCENT' AND itemId = '' AND resolution = 'RAW' ORDER BY bucketStart ASC LIMIT 1",
            "SELECT * FROM MetricSeriesBucketEntity WHERE metric = 'CPU_USAGE_PERCENT' AND itemId = '' AND resolution = 'RAW' AND bucketStart IN (1, 2)"
        ]
    )
    fun `answers every repository query from an index`(sql: String) {
        // Given
        migrate()

        // When
        val plan = queryPlan(sql)

        // Then
        plan shouldContain "SEARCH MetricSeriesBucketEntity USING"
        plan shouldNotContain "TEMP B-TREE"
    }
}
