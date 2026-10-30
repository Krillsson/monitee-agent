package com.krillsson.sysapi.storagepool.md

import com.krillsson.sysapi.storagepool.StoragePoolDeviceState
import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class MdProviderTest {

    @TempDir
    lateinit var root: File

    private fun stat(
        members: List<MdStatMember> = emptyList(),
        bitmap: String? = "[UU]",
        resync: MdStatResync? = null
    ) = MdStatArray(
        device = "md0",
        activityState = "active",
        level = "raid1",
        members = members,
        totalBlocks = 1000L,
        bitmap = bitmap,
        resync = resync
    )

    private fun sysfs(
        arrayState: String? = "clean",
        degraded: Int? = 0,
        syncAction: String? = "idle",
        syncCompletedSectors: Pair<Long, Long>? = null
    ) = MdSysfsArray(
        device = "md0",
        arrayState = arrayState,
        degraded = degraded,
        level = "raid1",
        raidDisks = 2,
        chunkSizeBytes = null,
        metadataVersion = null,
        uuid = null,
        syncAction = syncAction,
        syncCompletedSectors = syncCompletedSectors,
        syncSpeedKbPerSec = null,
        mismatchCnt = null,
        devices = emptyList()
    )

    private fun recovery(doneBlocks: Long?, totalBlocks: Long?) = MdStatResync(
        action = "recovery",
        percentComplete = 2.5f,
        doneBlocks = doneBlocks,
        totalBlocks = totalBlocks,
        finishEtaMinutes = 19.6f,
        speedKbPerSec = 214L
    )

    private fun File.writeAttribute(name: String, value: String) {
        mkdirs()
        File(this, name).writeText(value)
    }

    @ParameterizedTest
    @CsvSource(
        "in_sync, 0, false, ONLINE",
        "spare, 1, false, REBUILDING",
        "spare, , false, SPARE",
        "faulty, , false, FAULTY",
        "spare, , true, UNKNOWN",
        "in_sync, 0, true, UNKNOWN"
    )
    fun `derives a member's state from sysfs`(state: String, slot: Int?, arrayIsInactive: Boolean, expected: StoragePoolDeviceState) {
        // Given
        val device = MdSysfsDevice(kname = "loop5", state = setOf(state), slot = slot, errors = 0)

        // When
        val deviceState = MdProvider.deviceState(device, emptySet(), arrayIsInactive)

        // Then
        deviceState shouldBe expected
    }

    @Test
    fun `takes rebuild progress from mdstat while the sysfs checkpoint lags behind`() {
        // Given
        val stat = stat(resync = recovery(doneBlocks = 6656, totalBlocks = 260096))
        val sysfs = sysfs(syncAction = "recover", syncCompletedSectors = 0L to 520192L)

        // When
        val scan = MdProvider.deriveScan(stat, sysfs).shouldNotBeNull()

        // Then
        scan.bytesProcessed shouldBe 6656L * 1024
        scan.bytesTotal shouldBe 260096L * 1024
        scan.percentComplete shouldBe 2.5f
    }

    @Test
    fun `falls back to sysfs progress when mdstat has no block counts`() {
        // Given
        val stat = stat(resync = recovery(doneBlocks = null, totalBlocks = null))
        val sysfs = sysfs(syncAction = "recover", syncCompletedSectors = 1000L to 4000L)

        // When
        val scan = MdProvider.deriveScan(stat, sysfs).shouldNotBeNull()

        // Then
        scan.bytesProcessed shouldBe 1000L * 512
        scan.bytesTotal shouldBe 4000L * 512
    }

    @Test
    fun `keeps an array's id when it goes inactive and its uuid reads as zeros`() {
        // Given
        val mdstat = File(root, "mdstat")
        val md5 = File(root, "sys/block/md5/md")
        val provider = MdProvider(mdstat, MdSysfsReader(File(root, "sys")))
        mdstat.writeText("Personalities : [raid5]\nmd5 : active raid5 loop4[4] loop3[1] loop2[0]\n      520192 blocks super 1.2 level 5, 512k chunk, algorithm 2 [3/3] [UUU]\n")
        md5.writeAttribute("array_state", "clean\n")
        md5.writeAttribute("uuid", "589f91b1-983e-d567-be4d-1cac8ce4abed\n")
        val activeId = provider.pools().single().id

        // When
        mdstat.writeText("Personalities : [raid5]\nmd5 : inactive loop4[4](S) loop2[0](S)\n      520192 blocks super 1.2\n")
        md5.writeAttribute("array_state", "inactive\n")
        md5.writeAttribute("uuid", "00000000-0000-0000-0000-000000000000\n")
        val inactivePool = provider.pools().single()

        // Then
        activeId shouldBe "md:589f91b1-983e-d567-be4d-1cac8ce4abed"
        inactivePool.id shouldBe activeId
        inactivePool.state shouldBe StoragePoolState.FAULTED
    }

    @Test
    fun `names an inactive array after its device when its uuid was never seen`() {
        // Given
        val mdstat = File(root, "mdstat")
        mdstat.writeText("Personalities : [raid5]\nmd5 : inactive loop4[4](S) loop2[0](S)\n      520192 blocks super 1.2\n")
        File(root, "sys/block/md5/md").writeAttribute("uuid", "00000000-0000-0000-0000-000000000000\n")

        // When
        val pool = MdProvider(mdstat, MdSysfsReader(File(root, "sys"))).pools().single()

        // Then
        pool.id shouldBe "md:md5"
    }

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
