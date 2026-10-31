package com.krillsson.sysapi.storagepool.unraid

import com.krillsson.sysapi.storagepool.StoragePoolDeviceRole
import com.krillsson.sysapi.storagepool.StoragePoolDeviceState
import com.krillsson.sysapi.storagepool.StoragePoolScanKind
import com.krillsson.sysapi.storagepool.StoragePoolScanState
import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UnraidProviderTest {

    @TempDir
    lateinit var tempDir: File

    private fun slot(slot: Int, status: String, device: String = "", diskId: String = "", diskSizeK: Long = 0L) = """
        diskNumber.$slot=$slot
        diskId.$slot=$diskId
        diskSize.$slot=$diskSizeK
        rdevStatus.$slot=$status
        rdevName.$slot=$device
        rdevNumErrors.$slot=0
    """.trimIndent()

    private fun assigned(slot: Int, device: String, status: String = "DISK_OK") =
        slot(slot, status, device, "WDC_WD80EFAX_$slot", 7814026532L)

    private fun providerFor(vararg slots: String, disksIni: String? = null): UnraidProvider {
        val mdstat = File(tempDir, "mdstat")
        mdstat.writeText((listOf("mdState=STARTED", "mdNumDisks=4", "sbNumDisks=30") + slots).joinToString("\n"))
        val ini = File(tempDir, "disks.ini")
        disksIni?.let { ini.writeText(it) }
        return UnraidProvider(mdstat, ini)
    }

    private fun iniSection(slotName: String, fsSizeK: Long, fsFreeK: Long, fsUsedK: Long) = """
        ["$slotName"]
        device="sdx"
        size="7814026532"
        fsSize="$fsSizeK"
        fsFree="$fsFreeK"
        fsUsed="$fsUsedK"
    """.trimIndent()

    private fun emptySlots(range: IntRange) = range.map { slot(it, "DISK_NP") }.toTypedArray()

    private fun array(
        mdState: String? = "STARTED",
        disks: List<UnraidDisk> = listOf(UnraidDisk(0, "DISK_OK", 0L)),
        resyncPosK: Long? = 0L,
        resyncTotalK: Long? = 0L,
        syncCompletedAtEpochSeconds: Long? = 1L,
        syncExitCode: Int? = 0
    ) = UnraidArray(
        mdState = mdState,
        numDisks = disks.size,
        resyncAction = null,
        resyncSizeK = null,
        resyncPosK = resyncPosK,
        resyncTotalK = resyncTotalK,
        syncStartedAtEpochSeconds = 1L,
        syncCompletedAtEpochSeconds = syncCompletedAtEpochSeconds,
        syncErrors = null,
        syncExitCode = syncExitCode,
        disks = disks
    )

    @Test
    fun `a started array with every disk ok is online`() {
        // Given / When
        val state = UnraidProvider.deriveState(array())

        // Then
        state shouldBe StoragePoolState.ONLINE
    }

    @Test
    fun `a started array with an unhealthy disk is degraded`() {
        // Given
        val degraded = array(disks = listOf(UnraidDisk(0, "DISK_OK", 0L), UnraidDisk(1, "DISK_DSBL", 12L)))

        // When
        val state = UnraidProvider.deriveState(degraded)

        // Then
        state shouldBe StoragePoolState.DEGRADED
    }

    @Test
    fun `a stopped array is unavailable`() {
        // Given / When
        val state = UnraidProvider.deriveState(array(mdState = "STOPPED"))

        // Then
        state shouldBe StoragePoolState.UNAVAIL
    }

    @Test
    fun `a new array is unknown`() {
        // Given / When
        val state = UnraidProvider.deriveState(array(mdState = "NEW_ARRAY"))

        // Then
        state shouldBe StoragePoolState.UNKNOWN
    }

    @Test
    fun `a running parity check is scanning`() {
        // Given
        val running = array(resyncPosK = 500_000L, resyncTotalK = 1_000_000L, syncCompletedAtEpochSeconds = 0L)

        // When
        val scan = UnraidProvider.deriveScan(running).shouldNotBeNull()

        // Then
        scan.state shouldBe StoragePoolScanState.SCANNING
        scan.percentComplete shouldBe 50.0f
    }

    @Test
    fun `a cancelled parity check is reported as cancelled`() {
        // Given
        val cancelled = array(
            resyncPosK = 200_000L,
            resyncTotalK = 1_000_000L,
            syncCompletedAtEpochSeconds = 2L,
            syncExitCode = -4
        )

        // When
        val scan = UnraidProvider.deriveScan(cancelled).shouldNotBeNull()

        // Then
        scan.state shouldBe StoragePoolScanState.CANCELED
    }

    @Test
    fun `an idle array has no scan`() {
        // Given / When
        val scan = UnraidProvider.deriveScan(array(resyncTotalK = 0L))

        // Then
        scan.shouldBeNull()
    }

    @Test
    fun `slots with no disk assigned are left out and do not degrade the array`() {
        // Given
        val provider = providerFor(
            assigned(0, "sdo"),
            assigned(1, "sdd"),
            assigned(2, "sdae"),
            *emptySlots(3..28),
            slot(29, "DISK_NP_DSBL")
        )

        // When
        val pool = provider.pools().single()

        // Then
        pool.state shouldBe StoragePoolState.ONLINE
        pool.devices.map { it.id } shouldBe listOf("parity", "disk1", "disk2")
        pool.devices.map { it.name } shouldBe listOf("sdo", "sdd", "sdae")
    }

    @Test
    fun `an assigned disk that is disabled and gone degrades the array`() {
        // Given
        val provider = providerFor(
            assigned(0, "sdo"),
            slot(1, "DISK_NP_DSBL", diskId = "WDC_WD80EFAX_1", diskSizeK = 7814026532L),
            *emptySlots(2..28),
            slot(29, "DISK_NP_DSBL")
        )

        // When
        val pool = provider.pools().single()

        // Then
        pool.state shouldBe StoragePoolState.DEGRADED
        pool.devices.single { it.id == "disk1" }.state shouldBe StoragePoolDeviceState.MISSING
    }

    @Test
    fun `slot 29 is the second parity disk`() {
        // Given
        val provider = providerFor(
            assigned(0, "sdo"),
            assigned(1, "sdd"),
            *emptySlots(2..28),
            assigned(29, "sdp")
        )

        // When
        val parity2 = provider.pools().single().devices.single { it.name == "sdp" }

        // Then
        parity2.id shouldBe "parity2"
        parity2.role shouldBe StoragePoolDeviceRole.PARITY
    }

    @Test
    fun `a running disk reconstruction is a rebuild`() {
        // Given
        val rebuilding = array(resyncPosK = 100L, resyncTotalK = 1_000L, syncCompletedAtEpochSeconds = 0L)
            .copy(resyncAction = "recon D5")

        // When
        val scan = UnraidProvider.deriveScan(rebuilding).shouldNotBeNull()

        // Then
        scan.kind shouldBe StoragePoolScanKind.REBUILD
    }

    @Test
    fun `the array is sized by the data disks' filesystems and leaves parity out`() {
        // Given
        val provider = providerFor(
            assigned(0, "sdo"),
            assigned(1, "sdd"),
            assigned(2, "sdae"),
            *emptySlots(3..28),
            slot(29, "DISK_NP_DSBL"),
            disksIni = listOf(
                """["parity"]""",
                """device="sdo"""",
                """size="7814026532"""",
                iniSection("disk1", fsSizeK = 7_000_000L, fsFreeK = 1_000_000L, fsUsedK = 6_000_000L),
                iniSection("disk2", fsSizeK = 5_000_000L, fsFreeK = 2_000_000L, fsUsedK = 3_000_000L)
            ).joinToString("\n")
        )

        // When
        val pool = provider.pools().single()

        // Then
        pool.sizeBytes shouldBe 12_000_000L * 1024
        pool.allocatedBytes shouldBe 9_000_000L * 1024
        pool.freeBytes shouldBe 3_000_000L * 1024
    }

    @Test
    fun `without disks ini the array size is the raw size of the data disks`() {
        // Given
        val provider = providerFor(
            assigned(0, "sdo"),
            assigned(1, "sdd"),
            assigned(2, "sdae"),
            *emptySlots(3..28),
            slot(29, "DISK_NP_DSBL")
        )

        // When
        val pool = provider.pools().single()

        // Then
        pool.sizeBytes shouldBe 2 * 7814026532L * 1024
        pool.allocatedBytes.shouldBeNull()
        pool.freeBytes.shouldBeNull()
    }

    @Test
    fun `each disk reports its size from mdstat`() {
        // Given
        val provider = providerFor(assigned(0, "sdo"), assigned(1, "sdd"), *emptySlots(2..28), slot(29, "DISK_NP_DSBL"))

        // When
        val pool = provider.pools().single()

        // Then
        pool.devices.map { it.sizeBytes } shouldBe listOf(7814026532L * 1024, 7814026532L * 1024)
    }
}
