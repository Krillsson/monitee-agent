package com.krillsson.sysapi.storagepool.btrfs

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BtrfsSysfsReaderTest {

    @TempDir
    lateinit var btrfsRoot: File

    private val fsid = "4f1d2b3c-1111-2222-3333-444455556666"

    private fun dir(path: String): File = File(btrfsRoot, path).apply { mkdirs() }

    private fun File.writeAttribute(name: String, value: String) {
        File(this, name).writeText(value)
    }

    @Test
    fun `reads devices, block devices and allocation profiles of a filesystem`() {
        // Given
        dir(fsid).writeAttribute("label", "pool\n")
        dir("$fsid/devinfo/1").apply {
            writeAttribute("missing", "0\n")
            writeAttribute("replace_target", "0\n")
            writeAttribute("error_stats", "write_errs 1\nread_errs 2\nflush_errs 3\ncorruption_errs 4\ngeneration_errs 5\n")
        }
        dir("$fsid/devinfo/2").writeAttribute("missing", "1\n")
        dir("$fsid/devices/sdb").writeAttribute("size", "2048\n")
        dir("$fsid/allocation/data/raid1").writeAttribute("total_bytes", "0\n")
        dir("$fsid/allocation/data").writeAttribute("disk_used", "1000\n")
        dir("$fsid/allocation/metadata/raid1c3").writeAttribute("total_bytes", "0\n")
        dir("$fsid/allocation/metadata").writeAttribute("disk_used", "24\n")

        // When
        val filesystem = BtrfsSysfsReader(btrfsRoot).readFilesystems().single()

        // Then
        filesystem.fsid shouldBe fsid
        filesystem.label shouldBe "pool"
        filesystem.devices shouldBe listOf(
            BtrfsDevice(1, missing = false, replaceTarget = false, errorStats = BtrfsErrorStats(1, 2, 3, 4, 5)),
            BtrfsDevice(2, missing = true, replaceTarget = false, errorStats = null)
        )
        filesystem.blockDevices shouldBe listOf(BtrfsBlockDevice("sdb", 2048L * 512))
        filesystem.profilesByAllocationType shouldBe mapOf("data" to setOf("raid1"), "metadata" to setOf("raid1c3"))
        filesystem.allocatedBytes shouldBe 1024L
    }

    @Test
    fun `skips entries in the btrfs root that are not filesystems`() {
        // Given
        dir("features")

        // When
        val filesystems = BtrfsSysfsReader(btrfsRoot).readFilesystems()

        // Then
        filesystems.shouldBeEmpty()
    }

    @Test
    fun `reports no filesystems when btrfs is not loaded`() {
        // Given
        val reader = BtrfsSysfsReader(File(btrfsRoot, "absent"))

        // When / Then
        reader.exists() shouldBe false
        reader.readFilesystems().shouldBeEmpty()
    }

    @Test
    fun `leaves error counters out when the kernel says they are invalid`() {
        // Given / When
        val stats = BtrfsSysfsReader.parseErrorStats("invalid\n")

        // Then
        stats shouldBe null
    }
}
