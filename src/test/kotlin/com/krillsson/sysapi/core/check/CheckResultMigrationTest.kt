package com.krillsson.sysapi.core.check

import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager

class CheckResultMigrationTest {

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

    @Test
    fun `adds empty certificate columns to the results of an existing database`() {
        // Given
        migrate(target = "20")
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().executeUpdate(
                """
                INSERT INTO CheckResultEntity (id, checkId, checkType, timestamp, successful, latencyMs, message)
                VALUES ('result', 'check', 'HTTP', 0, 1, 42, 'OK')
                """.trimIndent()
            )
        }

        // When
        migrate()

        // Then
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().executeQuery(
                """
                SELECT latencyMs, certificateExpiresAt, certificateIssuedAt, certificateIssuer, certificateSubject
                FROM CheckResultEntity WHERE id = 'result'
                """.trimIndent()
            ).use { row ->
                row.next() shouldBe true
                row.getLong("latencyMs") shouldBe 42
                row.getObject("certificateExpiresAt") shouldBe null
                row.getObject("certificateIssuedAt") shouldBe null
                row.getObject("certificateIssuer") shouldBe null
                row.getObject("certificateSubject") shouldBe null
            }
        }
    }
}
