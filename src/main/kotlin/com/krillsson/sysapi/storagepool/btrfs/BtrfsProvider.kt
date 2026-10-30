package com.krillsson.sysapi.storagepool.btrfs

import com.krillsson.sysapi.storagepool.StoragePool
import com.krillsson.sysapi.storagepool.StoragePoolDataSource
import com.krillsson.sysapi.storagepool.StoragePoolDevice
import com.krillsson.sysapi.storagepool.StoragePoolDeviceRole
import com.krillsson.sysapi.storagepool.StoragePoolDeviceState
import com.krillsson.sysapi.storagepool.StoragePoolKind
import com.krillsson.sysapi.storagepool.StoragePoolProvider
import com.krillsson.sysapi.storagepool.StoragePoolState
import org.springframework.stereotype.Component

@Component
class BtrfsProvider(
    private val sysfsReader: BtrfsSysfsReader = BtrfsSysfsReader()
) : StoragePoolProvider {

    companion object {
        private val missingDevicesToleratedByProfile = mapOf(
            "single" to 0,
            "dup" to 0,
            "raid0" to 0,
            "raid1" to 1,
            "raid10" to 1,
            "raid5" to 1,
            "raid1c3" to 2,
            "raid6" to 2,
            "raid1c4" to 3
        )

        fun deriveState(filesystem: BtrfsFilesystem): StoragePoolState {
            val missing = filesystem.devices.count { it.missing }
            val profiles = filesystem.profilesByAllocationType.values.flatten()
            return when {
                filesystem.devices.isEmpty() -> StoragePoolState.UNKNOWN
                missing == 0 -> StoragePoolState.ONLINE
                profiles.isEmpty() -> StoragePoolState.DEGRADED
                missing > missingDevicesTolerated(profiles) -> StoragePoolState.FAULTED
                else -> StoragePoolState.DEGRADED
            }
        }

        private fun missingDevicesTolerated(profiles: List<String>): Int =
            profiles.minOf { missingDevicesToleratedByProfile[it] ?: 0 }

        fun redundancy(filesystem: BtrfsFilesystem): String? {
            val profiles = filesystem.profilesByAllocationType["data"]
                ?: filesystem.profilesByAllocationType["mixed"]
                ?: emptySet()
            return profiles.sorted().joinToString(",").takeIf { it.isNotEmpty() }
        }

        private fun deviceState(device: BtrfsDevice): StoragePoolDeviceState {
            return when {
                device.missing -> StoragePoolDeviceState.MISSING
                device.replaceTarget -> StoragePoolDeviceState.REBUILDING
                else -> StoragePoolDeviceState.ONLINE
            }
        }
    }

    override fun supported(): Boolean = sysfsReader.exists()

    override fun pools(): List<StoragePool> {
        return when {
            supported() -> sysfsReader.readFilesystems().map(::toStoragePool)
            else -> emptyList()
        }
    }

    private fun toStoragePool(filesystem: BtrfsFilesystem): StoragePool {
        val onlyBlockDevice = filesystem.blockDevices.singleOrNull()?.takeIf { filesystem.devices.size == 1 }
        val devices = filesystem.devices.map { device ->
            val stats = device.errorStats
            StoragePoolDevice(
                id = device.devid.toString(),
                name = onlyBlockDevice?.kname ?: "devid ${device.devid}",
                path = onlyBlockDevice?.let { "/dev/${it.kname}" },
                role = StoragePoolDeviceRole.DATA,
                state = deviceState(device),
                readErrors = stats?.readErrors,
                writeErrors = stats?.let { it.writeErrors + it.flushErrors },
                checksumErrors = stats?.let { it.corruptionErrors + it.generationErrors },
                sizeBytes = onlyBlockDevice?.sizeBytes
            )
        }
        val sizeBytes = filesystem.blockDevices.mapNotNull { it.sizeBytes }.takeIf { it.isNotEmpty() }?.sum()
        return StoragePool(
            id = "btrfs:${filesystem.fsid}",
            name = filesystem.label ?: filesystem.fsid,
            kind = StoragePoolKind.BTRFS,
            state = deriveState(filesystem),
            statusMessage = null,
            redundancy = redundancy(filesystem),
            sizeBytes = sizeBytes,
            allocatedBytes = filesystem.allocatedBytes,
            freeBytes = if (sizeBytes != null && filesystem.allocatedBytes != null) sizeBytes - filesystem.allocatedBytes else null,
            devices = devices,
            scan = null,
            dataSource = StoragePoolDataSource.KERNEL_INTERFACE
        )
    }
}
