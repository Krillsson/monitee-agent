package com.krillsson.sysapi.storagepool.md

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MdSysfsReaderTest {

    @TempDir
    lateinit var sysRoot: File

    private fun mdDir(device: String): File {
        val dir = File(sysRoot, "block/$device/md")
        dir.mkdirs()
        return dir
    }

    private fun File.writeAttribute(name: String, value: String) {
        File(this, name).writeText(value)
    }

    @Test
    fun `reads array attributes from sysfs`() {
        // Given
        val md0 = mdDir("md0")
        md0.writeAttribute("array_state", "clean\n")
        md0.writeAttribute("degraded", "0\n")
        md0.writeAttribute("level", "raid1\n")
        md0.writeAttribute("raid_disks", "2\n")
        md0.writeAttribute("chunk_size", "0\n")
        md0.writeAttribute("metadata_version", "1.2\n")
        md0.writeAttribute("uuid", "abcd1234:...\n")
        md0.writeAttribute("sync_action", "idle\n")
        md0.writeAttribute("sync_completed", "none\n")
        md0.writeAttribute("mismatch_cnt", "0\n")

        // When
        val arrays = MdSysfsReader(sysRoot).readArrays()

        // Then
        arrays shouldHaveSize 1
        val array = arrays.single()
        array.device shouldBe "md0"
        array.arrayState shouldBe "clean"
        array.degraded shouldBe 0
        array.level shouldBe "raid1"
        array.raidDisks shouldBe 2
        array.metadataVersion shouldBe "1.2"
        array.syncAction shouldBe "idle"
        array.syncCompletedSectors shouldBe null
        array.mismatchCnt shouldBe 0L
    }

    @Test
    fun `parses sync_completed in its done over total sectors form`() {
        // Given
        val md0 = mdDir("md0")
        md0.writeAttribute("array_state", "clean\n")
        md0.writeAttribute("sync_action", "resync\n")
        md0.writeAttribute("sync_completed", "488315232 / 976630464\n")
        md0.writeAttribute("sync_speed", "60000\n")

        // When
        val array = MdSysfsReader(sysRoot).readArrays().single()

        // Then
        array.syncCompletedSectors shouldBe (488315232L to 976630464L)
        array.syncSpeedKbPerSec shouldBe 60000L
    }

    @Test
    fun `reads per-device state with multiple flags`() {
        // Given
        val md0 = mdDir("md0")
        md0.writeAttribute("array_state", "clean\n")
        val dev0 = File(md0, "dev-sda1").apply { mkdirs() }
        dev0.writeAttribute("state", "in_sync,write_mostly\n")
        dev0.writeAttribute("errors", "3\n")
        val dev1 = File(md0, "dev-sdb1").apply { mkdirs() }
        dev1.writeAttribute("state", "faulty\n")
        dev1.writeAttribute("errors", "0\n")

        // When
        val array = MdSysfsReader(sysRoot).readArrays().single()

        // Then
        array.devices shouldHaveSize 2
        val sda1 = array.devices.first { it.kname == "sda1" }
        sda1.state shouldBe setOf("in_sync", "write_mostly")
        sda1.errors shouldBe 3L
        val sdb1 = array.devices.first { it.kname == "sdb1" }
        sdb1.state shouldBe setOf("faulty")
    }

    @Test
    fun `returns nothing when there is no block directory`() {
        // Given / When
        val arrays = MdSysfsReader(sysRoot).readArrays()

        // Then
        arrays.shouldBeEmpty()
    }
}
