package com.krillsson.sysapi.storagepool.md

import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MdProviderTest {

    private fun stat(members: List<MdStatMember> = emptyList(), bitmap: String? = "[UU]") = MdStatArray(
        device = "md0",
        activityState = "active",
        level = "raid1",
        members = members,
        totalBlocks = 1000L,
        bitmap = bitmap,
        resync = null
    )

    private fun sysfs(arrayState: String? = "clean", degraded: Int? = 0) = MdSysfsArray(
        device = "md0",
        arrayState = arrayState,
        degraded = degraded,
        level = "raid1",
        raidDisks = 2,
        chunkSizeBytes = null,
        metadataVersion = null,
        uuid = null,
        syncAction = "idle",
        syncCompletedSectors = null,
        syncSpeedKbPerSec = null,
        mismatchCnt = null,
        devices = emptyList()
    )

    @Test
    fun `a clean array with nothing degraded is online`() {
        // Given / When
        val state = MdProvider.deriveState(stat(), sysfs())

        // Then
        state shouldBe StoragePoolState.ONLINE
    }

    @Test
    fun `a non-zero degraded count from sysfs is degraded`() {
        // Given / When
        val state = MdProvider.deriveState(stat(), sysfs(degraded = 1))

        // Then
        state shouldBe StoragePoolState.DEGRADED
    }

    @Test
    fun `an inactive array is faulted`() {
        // Given / When
        val state = MdProvider.deriveState(stat(), sysfs(arrayState = "inactive"))

        // Then
        state shouldBe StoragePoolState.FAULTED
    }

    @Test
    fun `a faulty member flags the array as degraded even without sysfs`() {
        // Given
        val statWithFaultyMember = stat(members = listOf(MdStatMember("sdb1", 1, setOf('F'))))

        // When
        val state = MdProvider.deriveState(statWithFaultyMember, sysfs = null)

        // Then
        state shouldBe StoragePoolState.DEGRADED
    }

    @Test
    fun `a bitmap gap flags the array as degraded when sysfs is unavailable`() {
        // Given / When
        val state = MdProvider.deriveState(stat(bitmap = "[U_]"), sysfs = null)

        // Then
        state shouldBe StoragePoolState.DEGRADED
    }
}
