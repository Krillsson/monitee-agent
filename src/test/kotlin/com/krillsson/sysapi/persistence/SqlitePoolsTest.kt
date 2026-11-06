package com.krillsson.sysapi.persistence

import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SqlitePoolsTest {

    @TempDir
    lateinit var directory: Path

    private lateinit var writer: HikariDataSource
    private lateinit var reader: HikariDataSource

    @BeforeEach
    fun setUp() {
        val url = "jdbc:sqlite:${directory.resolve("database.sqlite")}"
        writer = SqlitePools.writer(url)
        reader = SqlitePools.reader(url)
        writer.connection.use { it.createStatement().executeUpdate("CREATE TABLE sample (value INT NOT NULL)") }
    }

    @AfterEach
    fun tearDown() {
        reader.close()
        writer.close()
    }

    private fun Connection.count(): Int = createStatement().executeQuery("SELECT count(*) FROM sample").use {
        it.next()
        it.getInt(1)
    }

    private fun Connection.insertInTransaction(value: Int) {
        autoCommit = false
        createStatement().executeUpdate("INSERT INTO sample (value) VALUES ($value)")
        commit()
        autoCommit = true
    }

    @Test
    fun `puts the database in write-ahead log mode`() {
        // When
        val mode = writer.connection.use { connection ->
            connection.createStatement().executeQuery("PRAGMA journal_mode").use {
                it.next()
                it.getString(1)
            }
        }

        // Then
        mode shouldBe "wal"
    }

    @Test
    fun `lets the writer commit while a read transaction is open`() {
        reader.connection.use { readConnection ->
            // Given
            readConnection.autoCommit = false
            readConnection.count() shouldBe 0

            // When
            writer.connection.use { it.insertInTransaction(1) }

            // Then
            readConnection.count() shouldBe 0
            readConnection.commit()
            readConnection.count() shouldBe 1
        }
    }

    @Test
    fun `refuses writes on a reader connection`() {
        // When
        val error = reader.connection.use { connection ->
            shouldThrow<SQLException> {
                connection.createStatement().executeUpdate("INSERT INTO sample (value) VALUES (1)")
            }
        }

        // Then
        error.message shouldContain "readonly"
    }

    @Test
    fun `reads and writes concurrently without busy errors`() {
        // Given
        val writes = 200
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val executor = Executors.newFixedThreadPool(4)

        // When
        executor.submit {
            runCatching { repeat(writes) { value -> writer.connection.use { it.insertInTransaction(value) } } }
                .onFailure { errors += it }
        }
        repeat(3) {
            executor.submit {
                runCatching {
                    repeat(writes) { reader.connection.use { connection -> connection.count() } }
                }.onFailure { errors += it }
            }
        }
        executor.shutdown()
        executor.awaitTermination(60, TimeUnit.SECONDS) shouldBe true

        // Then
        errors.shouldBeEmpty()
        reader.connection.use { it.count() } shouldBe writes
    }
}
