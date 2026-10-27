package com.krillsson.sysapi.storagepool.zfs

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ZfsKstatReaderTest {

    @TempDir
    lateinit var kstatRoot: File

    private fun pool(name: String, state: String) {
        File(kstatRoot, name).apply { mkdirs() }.resolve("state").writeText(state)
    }

    @Test
    fun `reads the state of every imported pool`() {
        // Given
        pool("tank", "ONLINE\n")
        pool("backup", "DEGRADED\n")

        // When
        val pools = ZfsKstatReader(kstatRoot).readPools()

        // Then
        pools shouldBe listOf(ZfsKstatPool("backup", "DEGRADED"), ZfsKstatPool("tank", "ONLINE"))
    }

    @Test
    fun `ignores module-wide kstats that are not pools`() {
        // Given
        File(kstatRoot, "arcstats").writeText("13 1 0x01 147 39984 ...\n")
        File(kstatRoot, "fm").mkdirs()

        // When
        val pools = ZfsKstatReader(kstatRoot).readPools()

        // Then
        pools.shouldBeEmpty()
    }

    @Test
    fun `reports no pools when the zfs module is not loaded`() {
        // Given
        val reader = ZfsKstatReader(File(kstatRoot, "absent"))

        // When / Then
        reader.exists() shouldBe false
        reader.readPools().shouldBeEmpty()
    }
}
