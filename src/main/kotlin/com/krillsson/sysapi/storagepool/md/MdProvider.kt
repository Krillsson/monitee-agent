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

@Component
class MdProvider(
    private val procMdstatFile: File = File("/proc/mdstat"),
    private val sysfsReader: MdSysfsReader = MdSysfsReader()
) : StoragePoolProvider {

    companion object {
        private const val SECTOR_SIZE_BYTES = 512L

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
            if (syncAction == null || syncAction == "idle") return null
            val kind = when (syncAction) {
                "resync", "reshape" -> StoragePoolScanKind.RESILVER
                "recover" -> StoragePoolScanKind.RESILVER
                "check", "repair" -> StoragePoolScanKind.SCRUB
                else -> StoragePoolScanKind.SCRUB
            }
            val (doneSectors, totalSectors) = sysfs.syncCompletedSectors ?: (null to null)
            val percent = stat.resync?.percentComplete
                ?: if (doneSectors != null && totalSectors != null && totalSectors > 0) {
                    (doneSectors * 100f) / totalSectors
                } else null
            return StoragePoolScan(
                kind = kind,
                state = StoragePoolScanState.SCANNING,
                percentComplete = percent,
                bytesProcessed = doneSectors?.times(SECTOR_SIZE_BYTES),
                bytesTotal = totalSectors?.times(SECTOR_SIZE_BYTES),
                startedAt = null,
                endedAt = null,
                estimatedSecondsRemaining = stat.resync?.finishEtaMinutes?.let { (it * 60).toLong() },
                errors = sysfs.mismatchCnt
            )
        }

        private fun deviceState(sysfsDevice: MdSysfsDevice?, memberFlags: Set<Char>): StoragePoolDeviceState {
            return when {
                sysfsDevice == null -> StoragePoolDeviceState.MISSING
                "faulty" in sysfsDevice.state || 'F' in memberFlags -> StoragePoolDeviceState.FAULTY
                "spare" in sysfsDevice.state || 'S' in memberFlags -> StoragePoolDeviceState.SPARE
                "in_sync" in sysfsDevice.state -> StoragePoolDeviceState.ONLINE
                else -> StoragePoolDeviceState.UNKNOWN
            }
        }
    }

    override fun supported(): Boolean = procMdstatFile.isFile

    override fun pools(): List<StoragePool> {
        if (!supported()) return emptyList()
        val statArrays = MdStatParser.parse(procMdstatFile.readText())
        if (statArrays.isEmpty()) return emptyList()
        val sysfsArrays = sysfsReader.readArrays().associateBy { it.device }
        return statArrays.map { stat -> toStoragePool(stat, sysfsArrays[stat.device]) }
    }

    private fun toStoragePool(stat: MdStatArray, sysfs: MdSysfsArray?): StoragePool {
        val sysfsDevicesByKname = sysfs?.devices?.associateBy { it.kname } ?: emptyMap()
        val devices = stat.members.map { member ->
            val sysfsDevice = sysfsDevicesByKname[member.name]
            StoragePoolDevice(
                id = member.name,
                name = member.name,
                path = "/dev/${member.name}",
                role = StoragePoolDeviceRole.DATA,
                state = deviceState(sysfsDevice, member.flags),
                readErrors = null,
                writeErrors = null,
                checksumErrors = sysfsDevice?.errors,
                sizeBytes = null
            )
        }
        return StoragePool(
            id = "md:${sysfs?.uuid ?: stat.device}",
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
