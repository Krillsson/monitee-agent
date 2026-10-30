package com.krillsson.sysapi.storagepool.zfs

import com.krillsson.sysapi.storagepool.StoragePool
import com.krillsson.sysapi.storagepool.StoragePoolDataSource
import com.krillsson.sysapi.storagepool.StoragePoolKind
import com.krillsson.sysapi.storagepool.StoragePoolProvider
import com.krillsson.sysapi.storagepool.StoragePoolState
import org.springframework.stereotype.Component

@Component
class ZfsProvider(
    private val kstatReader: ZfsKstatReader = ZfsKstatReader()
) : StoragePoolProvider {

    companion object {
        fun deriveState(kstatState: String): StoragePoolState {
            return when (kstatState) {
                "ONLINE" -> StoragePoolState.ONLINE
                "DEGRADED" -> StoragePoolState.DEGRADED
                "FAULTED", "SUSPENDED", "SPLIT" -> StoragePoolState.FAULTED
                "UNAVAIL", "OFFLINE", "REMOVED" -> StoragePoolState.UNAVAIL
                else -> StoragePoolState.UNKNOWN
            }
        }
    }

    override fun supported(): Boolean = kstatReader.exists()

    override fun pools(): List<StoragePool> {
        return when {
            supported() -> kstatReader.readPools().map(::toStoragePool)
            else -> emptyList()
        }
    }

    private fun toStoragePool(pool: ZfsKstatPool): StoragePool {
        return StoragePool(
            id = "zfs:${pool.name}",
            name = pool.name,
            kind = StoragePoolKind.ZFS,
            state = deriveState(pool.state),
            statusMessage = pool.state,
            redundancy = null,
            sizeBytes = null,
            allocatedBytes = null,
            freeBytes = null,
            devices = emptyList(),
            scan = null,
            dataSource = StoragePoolDataSource.KERNEL_INTERFACE
        )
    }
}
