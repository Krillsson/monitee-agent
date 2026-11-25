package com.krillsson.sysapi.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class JsonFileTest {

    @TempDir
    lateinit var dir: File

    private class CounterFile(directory: File) :
        JsonFile<Map<String, Int>>("counter.json", object : TypeReference<Map<String, Int>>() {}, ObjectMapper(), directory)

    @Test
    fun `reading a missing file returns null`() {
        // Given
        val file = CounterFile(dir)

        // When
        val result = file.read()

        // Then
        result shouldBe null
    }

    @Test
    fun `reads back what was written`() {
        // Given
        val file = CounterFile(dir)

        // When
        file.write(mapOf("a" to 1, "b" to 2))

        // Then
        file.read() shouldBe mapOf("a" to 1, "b" to 2)
    }

    @Test
    fun `writing replaces the previous content and leaves no temp file`() {
        // Given
        val file = CounterFile(dir)
        file.write(mapOf("a" to 1, "b" to 2))

        // When
        file.write(mapOf("c" to 3))

        // Then
        file.read() shouldBe mapOf("c" to 3)
        File(dir, "counter.json.tmp").exists() shouldBe false
    }

    @Test
    fun `a leftover partial temp file does not affect reading and is overwritten by the next write`() {
        // Given
        val file = CounterFile(dir)
        file.write(mapOf("a" to 1))
        File(dir, "counter.json.tmp").writeText("{\"a\": 99, \"b\"")

        // When
        val before = file.read()
        file.write(mapOf("a" to 2))

        // Then
        before shouldBe mapOf("a" to 1)
        file.read() shouldBe mapOf("a" to 2)
        File(dir, "counter.json.tmp").exists() shouldBe false
    }

    @Test
    fun `concurrent updates do not lose increments`() {
        // Given
        val file = CounterFile(dir)
        val threads = 8
        val perThread = 25
        val start = CountDownLatch(1)

        // When
        val workers = (1..threads).map {
            thread {
                start.await()
                repeat(perThread) {
                    file.update { current -> mapOf("count" to ((current?.get("count") ?: 0) + 1)) }
                }
            }
        }
        start.countDown()
        workers.forEach { it.join() }

        // Then
        file.read() shouldBe mapOf("count" to threads * perThread)
    }
}
