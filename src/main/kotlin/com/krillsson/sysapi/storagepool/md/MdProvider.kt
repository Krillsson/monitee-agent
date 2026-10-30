package com.krillsson.sysapi.storagepool.md

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
import java.util.concurrent.ConcurrentHashMap

@Component
class MdProvider(
    private val procMdstatFile: File = File("/proc/mdstat"),
    private val sysfsReader: MdSysfsReader = MdSysfsReader()
) : StoragePoolProvider {

    companion object {
        private const val SECTOR_SIZE_BYTES = 512L
        private const val BLOCK_SIZE_BYTES = 1024L

        fun deriveState(stat: MdStatArray, sysfs: MdSysfsArray?): StoragePoolState {
            return when {
                sysfs?.arrayState == "inactive" -> StoragePoolState.FAULTED
                (sysfs?.degraded ?: 0) > 0 -> StoragePoolState.DEGRADED
                stat.members.any { 'F' in it.flags } -> StoragePoolState.DEGRADED
                stat.bitmap?.contains('_') == true -> StoragePoolState.DEGRADED
                sysfs?.arrayState in setOf("clean", "active", "active-idle", "readonly", "read-auto") -> StoragePoolState.ONLINE
                stat.activityState == "active" -> StoragePoolState.ONLINE
                else -> StoragePoolState.UNKNOWN
            }
        }

        fun deriveScan(stat: MdStatArray, sysfs: MdSysfsArray?): StoragePoolScan? {
            val syncAction = sysfs?.syncAction
            return when {
                sysfs == null || syncAction == null || syncAction == "idle" -> null
                else -> runningScan(stat, sysfs, syncAction)
            }
        }

        private fun runningScan(stat: MdStatArray, sysfs: MdSysfsArray, syncAction: String): StoragePoolScan {
            val kind = when (syncAction) {
                "resync", "reshape" -> StoragePoolScanKind.RESILVER
                "recover" -> StoragePoolScanKind.RESILVER
                "check", "repair" -> StoragePoolScanKind.SCRUB
                else -> StoragePoolScanKind.SCRUB
            }
            val (bytesProcessed, bytesTotal) = syncProgressBytes(stat.resync, sysfs.syncCompletedSectors) ?: (null to null)
            return StoragePoolScan(
                kind = kind,
                state = StoragePoolScanState.SCANNING,
                percentComplete = stat.resync?.percentComplete ?: percentOf(bytesProcessed, bytesTotal),
                bytesProcessed = bytesProcessed,
                bytesTotal = bytesTotal,
                startedAt = null,
                endedAt = null,
                estimatedSecondsRemaining = stat.resync?.finishEtaMinutes?.let { (it * 60).toLong() },
                errors = sysfs.mismatchCnt
            )
        }

        private fun syncProgressBytes(resync: MdStatResync?, syncCompletedSectors: Pair<Long, Long>?): Pair<Long, Long>? {
            val doneBlocks = resync?.doneBlocks
            val totalBlocks = resync?.totalBlocks
            return when {
                doneBlocks != null && totalBlocks != null -> doneBlocks * BLOCK_SIZE_BYTES to totalBlocks * BLOCK_SIZE_BYTES
                syncCompletedSectors != null -> syncCompletedSectors.first * SECTOR_SIZE_BYTES to syncCompletedSectors.second * SECTOR_SIZE_BYTES
                else -> null
            }
        }

        private fun percentOf(done: Long?, total: Long?): Float? {
            return when {
                done == null || total == null || total <= 0 -> null
                else -> (done * 100f) / total
            }
        }

        fun deviceState(sysfsDevice: MdSysfsDevice?, memberFlags: Set<Char>, arrayIsInactive: Boolean): StoragePoolDeviceState {
            return when {
                arrayIsInactive -> StoragePoolDeviceState.UNKNOWN
                sysfsDevice == null -> StoragePoolDeviceState.MISSING
                "faulty" in sysfsDevice.state || 'F' in memberFlags -> StoragePoolDeviceState.FAULTY
                isRebuildTarget(sysfsDevice) -> StoragePoolDeviceState.REBUILDING
                "spare" in sysfsDevice.state || 'S' in memberFlags -> StoragePoolDeviceState.SPARE
                "in_sync" in sysfsDevice.state -> StoragePoolDeviceState.ONLINE
                else -> StoragePoolDeviceState.UNKNOWN
            }
        }

        private fun isRebuildTarget(device: MdSysfsDevice): Boolean = "spare" in device.state && device.slot != null
    }

    private val lastKnownUuidByDevice = ConcurrentHashMap<String, String>()

    override fun supported(): Boolean = procMdstatFile.isFile

    override fun pools(): List<StoragePool> {
        val statArrays = readStatArrays()
        return when {
            statArrays.isEmpty() -> emptyList()
            else -> toStoragePools(statArrays)
        }
    }

    private fun readStatArrays(): List<MdStatArray> {
        return when {
            supported() -> MdStatParser.parse(procMdstatFile.readText())
            else -> emptyList()
        }
    }

    private fun toStoragePools(statArrays: List<MdStatArray>): List<StoragePool> {
        val sysfsArrays = sysfsReader.readArrays().associateBy { it.device }
        return statArrays.map { stat -> toStoragePool(stat, sysfsArrays[stat.device]) }
    }

    private fun rememberedUuid(device: String, uuid: String?): String? {
        return when (uuid) {
            null -> lastKnownUuidByDevice[device]
            else -> uuid.also { lastKnownUuidByDevice[device] = it }
        }
    }

    private fun toStoragePool(stat: MdStatArray, sysfs: MdSysfsArray?): StoragePool {
        val sysfsDevicesByKname = sysfs?.devices?.associateBy { it.kname } ?: emptyMap()
        val arrayIsInactive = sysfs?.arrayState == "inactive"
        val devices = stat.members.map { member ->
            val sysfsDevice = sysfsDevicesByKname[member.name]
            StoragePoolDevice(
                id = member.name,
                name = member.name,
                path = "/dev/${member.name}",
                role = StoragePoolDeviceRole.DATA,
                state = deviceState(sysfsDevice, member.flags, arrayIsInactive),
                readErrors = null,
                writeErrors = null,
                checksumErrors = sysfsDevice?.errors,
                sizeBytes = null
            )
        }
        return StoragePool(
            id = "md:${rememberedUuid(stat.device, sysfs?.uuid) ?: stat.device}",
            name = stat.device,
            kind = StoragePoolKind.MDADM,
            state = deriveState(stat, sysfs),
            statusMessage = null,
            redundancy = sysfs?.level ?: stat.level,
            sizeBytes = stat.totalBlocks?.times(1024),
            allocatedBytes = null,
            freeBytes = null,
            devices = devices,
            scan = deriveScan(stat, sysfs),
            dataSource = StoragePoolDataSource.KERNEL_INTERFACE
        )
    }
}
