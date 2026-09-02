package com.krillsson.sysapi.storagepool.unraid

import com.krillsson.sysapi.storagepool.StoragePool
import com.krillsson.sysapi.storagepool.StoragePoolDataSource
import com.krillsson.sysapi.storagepool.StoragePoolDevice
import com.krillsson.sysapi.storagepool.StoragePoolDeviceRole
import com.krillsson.sysapi.storagepool.StoragePoolDeviceState
import com.krillsson.sysapi.storagepool.StoragePoolKind
import com.krillsson.sysapi.storagepool.StoragePoolProvider
import com.krillsson.sysapi.storagepool.StoragePoolScan
import com.krillsson.sysapi.storagepool.StoragePoolScanKind
import com.krillsson.sysapi.storagepool.StoragePoolScanState
import com.krillsson.sysapi.storagepool.StoragePoolState
import org.springframework.stereotype.Component
import java.io.File

@Component
class UnraidProvider(
    private val procMdstatFile: File = File("/proc/mdstat"),
    private val disksIniFile: File = File("/var/local/emhttp/disks.ini")
) : StoragePoolProvider {

    companion object {
        fun deriveState(array: UnraidArray): StoragePoolState {
            val anyDiskUnhealthy = array.disks.any { it.status != null && it.status != "DISK_OK" }
            return when (array.mdState) {
                "STARTED" -> if (anyDiskUnhealthy) StoragePoolState.DEGRADED else StoragePoolState.ONLINE
                "STOPPED" -> StoragePoolState.UNAVAIL
                "NEW_ARRAY" -> StoragePoolState.UNKNOWN
                "DISABLE_DISK", "RECON_DISK", "SWAP_DSBL" -> StoragePoolState.DEGRADED
                null -> StoragePoolState.UNKNOWN
                else -> StoragePoolState.UNKNOWN
            }
        }

        fun deriveScan(array: UnraidArray): StoragePoolScan? {
            val total = array.resyncTotalK ?: 0L
            if (total <= 0L) return null
            val position = array.resyncPosK ?: 0L
            val isRunning = array.syncCompletedAtEpochSeconds == 0L
            return StoragePoolScan(
                kind = StoragePoolScanKind.PARITY_CHECK,
                state = if (isRunning) StoragePoolScanState.SCANNING else if (array.syncExitCode == -4) StoragePoolScanState.CANCELED else StoragePoolScanState.FINISHED,
                percentComplete = (position * 100f) / total,
                bytesProcessed = position * 1024,
                bytesTotal = total * 1024,
                startedAt = array.syncStartedAtEpochSeconds?.let { java.time.Instant.ofEpochSecond(it) },
                endedAt = array.syncCompletedAtEpochSeconds?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it) },
                estimatedSecondsRemaining = null,
                errors = array.syncErrors
            )
        }

        private fun deviceState(status: String?): StoragePoolDeviceState {
            return when (status) {
                "DISK_OK" -> StoragePoolDeviceState.ONLINE
                "DISK_NP", "DISK_NP_MISSING" -> StoragePoolDeviceState.MISSING
                "DISK_INVALID" -> StoragePoolDeviceState.FAULTY
                "DISK_DSBL" -> StoragePoolDeviceState.FAULTY
                null -> StoragePoolDeviceState.UNKNOWN
                else -> StoragePoolDeviceState.UNKNOWN
            }
        }

        private fun slotName(slot: Int) = if (slot == 0) "parity" else "disk$slot"
    }

    override fun supported(): Boolean = readArray() != null

    override fun pools(): List<StoragePool> {
        val array = readArray() ?: return emptyList()
        return listOf(toStoragePool(array))
    }

    private fun readArray(): UnraidArray? {
        if (!procMdstatFile.isFile) return null
        return UnraidMdStatParser.parse(procMdstatFile.readText())
    }

    private fun readDiskInfoBySlotName(): Map<String, UnraidDiskInfo> {
        if (!disksIniFile.isFile) return emptyMap()
        return runCatching { UnraidDisksIniParser.parse(disksIniFile.readText()) }
            .getOrDefault(emptyList())
            .associateBy { it.slotName }
    }

    private fun toStoragePool(array: UnraidArray): StoragePool {
        val diskInfoBySlotName = readDiskInfoBySlotName()
        val devices = array.disks.map { disk ->
            val slotName = slotName(disk.slot)
            val info = diskInfoBySlotName[slotName]
            StoragePoolDevice(
                id = slotName,
                name = info?.device ?: slotName,
                path = info?.device?.let { "/dev/$it" },
                role = if (disk.slot == 0) StoragePoolDeviceRole.PARITY else StoragePoolDeviceRole.DATA,
                state = deviceState(disk.status),
                readErrors = null,
                writeErrors = null,
                checksumErrors = disk.numErrors ?: info?.numErrors,
                sizeBytes = null
            )
        }
        return StoragePool(
            id = "unraid:array",
            name = "Unraid array",
            kind = StoragePoolKind.UNRAID_ARRAY,
            state = deriveState(array),
            statusMessage = array.mdState,
            redundancy = "parity",
            sizeBytes = null,
            allocatedBytes = null,
            freeBytes = null,
            devices = devices,
            scan = deriveScan(array),
            dataSource = StoragePoolDataSource.KERNEL_INTERFACE
        )
    }
}
