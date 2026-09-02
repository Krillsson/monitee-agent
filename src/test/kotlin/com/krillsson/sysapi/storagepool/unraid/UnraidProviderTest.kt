package com.krillsson.sysapi.storagepool.unraid

import com.krillsson.sysapi.storagepool.StoragePoolScanState
import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UnraidProviderTest {

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
}
