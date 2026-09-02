package com.krillsson.sysapi.storagepool.md

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MdStatParserTest {

    @Test
    fun `parses a healthy raid1 array`() {
        // Given
        val text = """
            Personalities : [raid1]
            md0 : active raid1 sdb1[1] sda1[0]
                  976630464 blocks super 1.2 [2/2] [UU]

            unused devices: <none>
        """.trimIndent()

        // When
        val arrays = MdStatParser.parse(text)

        // Then
        arrays shouldHaveSize 1
        val array = arrays.single()
        array.device shouldBe "md0"
        array.activityState shouldBe "active"
        array.level shouldBe "raid1"
        array.totalBlocks shouldBe 976630464L
        array.bitmap shouldBe "[UU]"
        array.members.map { it.name } shouldBe listOf("sdb1", "sda1")
        array.members.all { it.flags.isEmpty() } shouldBe true
        array.resync.shouldBeNull()
    }

    @Test
    fun `parses a degraded array from its bitmap`() {
        // Given
        val text = """
            Personalities : [raid1]
            md0 : active raid1 sda1[0]
                  976630464 blocks super 1.2 [2/1] [U_]

            unused devices: <none>
        """.trimIndent()

        // When
        val array = MdStatParser.parse(text).single()

        // Then
        array.bitmap shouldBe "[U_]"
        array.members shouldHaveSize 1
    }

    @Test
    fun `flags a faulty member`() {
        // Given
        val text = """
            Personalities : [raid1]
            md0 : active raid1 sdb1[1](F) sda1[0]
                  976630464 blocks super 1.2 [2/1] [U_]

            unused devices: <none>
        """.trimIndent()

        // When
        val array = MdStatParser.parse(text).single()

        // Then
        val faulty = array.members.first { it.name == "sdb1" }
        faulty.flags shouldBe setOf('F')
        faulty.role shouldBe 1
    }

    @Test
    fun `flags a spare member`() {
        // Given
        val text = """
            Personalities : [raid5]
            md1 : active raid5 sdd1[3](S) sdc1[2] sdb1[1] sda1[0]
                  1953260544 blocks super 1.2 level 5, 64k chunk, algorithm 2 [3/3] [UUU]

            unused devices: <none>
        """.trimIndent()

        // When
        val array = MdStatParser.parse(text).single()

        // Then
        val spare = array.members.first { it.name == "sdd1" }
        spare.flags shouldBe setOf('S')
        array.level shouldBe "raid5"
    }

    @Test
    fun `parses a resync line with percentage and ETA`() {
        // Given
        val text = """
            Personalities : [raid1]
            md0 : active raid1 sdb1[1] sda1[0]
                  976630464 blocks super 1.2 [2/2] [UU]
                  [==========>..........]  resync = 50.0% (488315232/976630464) finish=120.5min speed=60000K/sec

            unused devices: <none>
        """.trimIndent()

        // When
        val array = MdStatParser.parse(text).single()

        // Then
        val resync = array.resync.shouldNotBeNull()
        resync.action shouldBe "resync"
        resync.percentComplete shouldBe 50.0f
        resync.finishEtaMinutes shouldBe 120.5f
        resync.speedKbPerSec shouldBe 60000L
    }

    @Test
    fun `parses an array with no redundancy`() {
        // Given
        val text = """
            Personalities : [raid0]
            md0 : active raid0 sda1[0] sdb1[1]
                  1953260928 blocks super 1.2 512k chunks

            unused devices: <none>
        """.trimIndent()

        // When
        val array = MdStatParser.parse(text).single()

        // Then
        array.level shouldBe "raid0"
        array.bitmap.shouldBeNull()
        array.totalBlocks shouldBe 1953260928L
    }

    @Test
    fun `parses the empty mdstat this dev VM has`() {
        // Given
        val text = """
            Personalities :
            unused devices: <none>
        """.trimIndent()

        // When
        val arrays = MdStatParser.parse(text)

        // Then
        arrays.shouldBeEmpty()
    }
}
