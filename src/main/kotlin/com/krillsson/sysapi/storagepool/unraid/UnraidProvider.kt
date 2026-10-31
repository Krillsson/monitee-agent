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
        private const val PARITY_SLOT = 0
        private const val PARITY2_SLOT = 29
        private const val KIB = 1024L

        fun isAssigned(disk: UnraidDisk): Boolean {
            val notPresent = disk.status == "DISK_NP" || disk.status == "DISK_NP_DSBL"
            return !(notPresent && disk.diskId == null && (disk.diskSizeK ?: 0L) == 0L)
        }

        fun deriveState(array: UnraidArray): StoragePoolState {
            val anyDiskUnhealthy = array.disks.filter(::isAssigned).any { it.status != null && it.status != "DISK_OK" }
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
            return when {
                total <= 0L -> null
                else -> scan(array, total)
            }
        }

        private fun scan(array: UnraidArray, total: Long): StoragePoolScan {
            val position = array.resyncPosK ?: 0L
            val isRunning = array.syncCompletedAtEpochSeconds == 0L
            return StoragePoolScan(
                kind = if (array.resyncAction?.startsWith("recon") == true) StoragePoolScanKind.REBUILD else StoragePoolScanKind.PARITY_CHECK,
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
                "DISK_NP", "DISK_NP_MISSING", "DISK_NP_DSBL" -> StoragePoolDeviceState.MISSING
                "DISK_INVALID", "DISK_WRONG", "DISK_DSBL" -> StoragePoolDeviceState.FAULTY
                "DISK_DSBL_NEW" -> StoragePoolDeviceState.REBUILDING
                null -> StoragePoolDeviceState.UNKNOWN
                else -> StoragePoolDeviceState.UNKNOWN
            }
        }

        private fun slotName(slot: Int) = when (slot) {
            PARITY_SLOT -> "parity"
            PARITY2_SLOT -> "parity2"
            else -> "disk$slot"
        }

        private fun isParity(slot: Int) = slot == PARITY_SLOT || slot == PARITY2_SLOT

        private fun <T> List<T>.sumOfKibOrNull(selector: (T) -> Long?): Long? {
            val values = mapNotNull(selector)
            return if (values.isEmpty()) null else values.sum() * KIB
        }
    }

    override fun supported(): Boolean = readArray() != null

    override fun pools(): List<StoragePool> {
        return when (val array = readArray()) {
            null -> emptyList()
            else -> listOf(toStoragePool(array))
        }
    }

    private fun readArray(): UnraidArray? {
        return when {
            procMdstatFile.isFile -> UnraidMdStatParser.parse(procMdstatFile.readText())
            else -> null
        }
    }

    private fun readDiskInfoBySlotName(): Map<String, UnraidDiskInfo> {
        return when {
            disksIniFile.isFile -> parseDisksIni().associateBy { it.slotName }
            else -> emptyMap()
        }
    }

    private fun parseDisksIni(): List<UnraidDiskInfo> =
        runCatching { UnraidDisksIniParser.parse(disksIniFile.readText()) }.getOrDefault(emptyList())

    private fun toStoragePool(array: UnraidArray): StoragePool {
        val diskInfoBySlotName = readDiskInfoBySlotName()
        val devices = array.disks.filter(::isAssigned).map { disk ->
            val slotName = slotName(disk.slot)
            val info = diskInfoBySlotName[slotName]
            StoragePoolDevice(
                id = slotName,
                name = info?.device ?: disk.deviceName ?: slotName,
                path = (info?.device ?: disk.deviceName)?.let { "/dev/$it" },
                role = if (isParity(disk.slot)) StoragePoolDeviceRole.PARITY else StoragePoolDeviceRole.DATA,
                state = deviceState(disk.status),
                readErrors = null,
                writeErrors = null,
                checksumErrors = disk.numErrors ?: info?.numErrors,
                sizeBytes = (disk.diskSizeK?.takeIf { it > 0 } ?: info?.sizeK)?.times(KIB)
            )
        }
        val dataDisks = array.disks.filter(::isAssigned).filterNot { isParity(it.slot) }
        val dataDiskInfo = dataDisks.mapNotNull { diskInfoBySlotName[slotName(it.slot)] }
        val fsSizeBytes = dataDiskInfo.sumOfKibOrNull { it.fsSizeK }
        return StoragePool(
            id = "unraid:array",
            name = "Unraid array",
            kind = StoragePoolKind.UNRAID_ARRAY,
            state = deriveState(array),
            statusMessage = array.mdState,
            redundancy = "parity",
            sizeBytes = fsSizeBytes ?: dataDisks.sumOfKibOrNull { it.diskSizeK },
            allocatedBytes = dataDiskInfo.sumOfKibOrNull { it.fsUsedK },
            freeBytes = dataDiskInfo.sumOfKibOrNull { it.fsFreeK },
            devices = devices,
            scan = deriveScan(array),
            dataSource = StoragePoolDataSource.KERNEL_INTERFACE
        )
    }
}
