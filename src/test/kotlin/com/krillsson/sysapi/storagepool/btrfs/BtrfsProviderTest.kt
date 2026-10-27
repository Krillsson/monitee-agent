package com.krillsson.sysapi.storagepool.btrfs

import com.krillsson.sysapi.storagepool.StoragePoolDeviceState
import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BtrfsProviderTest {

    private fun device(devid: Long, missing: Boolean = false, errorStats: BtrfsErrorStats? = null) =
        BtrfsDevice(devid, missing = missing, replaceTarget = false, errorStats = errorStats)

    private fun filesystem(
        devices: List<BtrfsDevice>,
        dataProfile: String = "raid1",
        metadataProfile: String = "raid1",
        blockDevices: List<BtrfsBlockDevice> = emptyList()
    ) = BtrfsFilesystem(
        fsid = "4f1d2b3c-1111-2222-3333-444455556666",
        label = null,
        devices = devices,
        blockDevices = blockDevices,
        profilesByAllocationType = mapOf("data" to setOf(dataProfile), "metadata" to setOf(metadataProfile)),
        allocatedBytes = null
    )

    @Test
    fun `a filesystem with every device present is online`() {
        // Given
        val filesystem = filesystem(listOf(device(1), device(2)))

        // When
        val state = BtrfsProvider.deriveState(filesystem)

        // Then
        state shouldBe StoragePoolState.ONLINE
    }

    @Test
    fun `error counters do not change the state, they are reported per device`() {
        // Given
        val filesystem = filesystem(listOf(device(1, errorStats = BtrfsErrorStats(0, 0, 0, 7, 0)), device(2)))

        // When
        val state = BtrfsProvider.deriveState(filesystem)

        // Then
        state shouldBe StoragePoolState.ONLINE
    }

    @ParameterizedTest
    @CsvSource(
        "raid1, raid1, 1, DEGRADED",
        "raid1, raid1, 2, FAULTED",
        "raid6, raid1c3, 2, DEGRADED",
        "raid6, raid1, 2, FAULTED",
        "raid10, raid1c4, 1, DEGRADED",
        "single, dup, 1, FAULTED",
        "raid0, raid1, 1, FAULTED"
    )
    fun `missing devices degrade the filesystem until the weakest profile can no longer cover them`(
        dataProfile: String,
        metadataProfile: String,
        missing: Int,
        expected: StoragePoolState
    ) {
        // Given
        val devices = (1L..4L).map { device(it, missing = it <= missing) }
        val filesystem = filesystem(devices, dataProfile, metadataProfile)

        // When
        val state = BtrfsProvider.deriveState(filesystem)

        // Then
        state shouldBe expected
    }

    @Test
    fun `a missing device with unknown profiles is degraded`() {
        // Given
        val filesystem = filesystem(listOf(device(1, missing = true), device(2))).copy(profilesByAllocationType = emptyMap())

        // When
        val state = BtrfsProvider.deriveState(filesystem)

        // Then
        state shouldBe StoragePoolState.DEGRADED
    }

    @Test
    fun `a filesystem with no devices listed is unknown`() {
        // Given / When
        val state = BtrfsProvider.deriveState(filesystem(emptyList()))

        // Then
        state shouldBe StoragePoolState.UNKNOWN
    }

    @Test
    fun `names the device after its block device when there is only one`() {
        // Given
        val reader = mockk<BtrfsSysfsReader>()
        every { reader.exists() } returns true
        every { reader.readFilesystems() } returns listOf(
            filesystem(
                listOf(device(1, errorStats = BtrfsErrorStats(1, 2, 3, 4, 5))),
                dataProfile = "single",
                metadataProfile = "dup",
                blockDevices = listOf(BtrfsBlockDevice("nvme0n1p1", 1000L))
            ).copy(allocatedBytes = 400L)
        )

        // When
        val pool = BtrfsProvider(reader).pools().single()

        // Then
        pool.id shouldBe "btrfs:4f1d2b3c-1111-2222-3333-444455556666"
        pool.redundancy shouldBe "single"
        pool.sizeBytes shouldBe 1000L
        pool.freeBytes shouldBe 600L
        val device = pool.devices.single()
        device.name shouldBe "nvme0n1p1"
        device.path shouldBe "/dev/nvme0n1p1"
        device.readErrors shouldBe 2L
        device.writeErrors shouldBe 4L
        device.checksumErrors shouldBe 9L
    }

    @Test
    fun `names devices by devid when they cannot be told apart`() {
        // Given
        val reader = mockk<BtrfsSysfsReader>()
        every { reader.exists() } returns true
        every { reader.readFilesystems() } returns listOf(
            filesystem(
                listOf(device(1), device(2, missing = true)),
                blockDevices = listOf(BtrfsBlockDevice("sdb", 1000L))
            )
        )

        // When
        val devices = BtrfsProvider(reader).pools().single().devices

        // Then
        devices.map { it.name } shouldBe listOf("devid 1", "devid 2")
        devices.map { it.path } shouldBe listOf(null, null)
        devices.map { it.state } shouldBe listOf(StoragePoolDeviceState.ONLINE, StoragePoolDeviceState.MISSING)
    }
}
