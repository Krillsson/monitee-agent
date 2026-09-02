package com.krillsson.sysapi.storagepool

import java.time.Instant

enum class StoragePoolKind {
    ZFS,
    MDADM,
    BTRFS,
    UNRAID_ARRAY
}

enum class StoragePoolState {
    ONLINE,
    UNKNOWN,
    DEGRADED,
    FAULTED,
    UNAVAIL
}

enum class StoragePoolDeviceRole {
    DATA,
    PARITY,
    SPARE,
    CACHE,
    LOG,
    SPECIAL
}

enum class StoragePoolDeviceState {
    ONLINE,
    REBUILDING,
    SPARE,
    FAULTY,
    MISSING,
    UNKNOWN
}

enum class StoragePoolScanKind {
    SCRUB,
    RESILVER,
    PARITY_CHECK,
    REBUILD,
    BALANCE
}

enum class StoragePoolScanState {
    SCANNING,
    FINISHED,
    CANCELED,
    PAUSED
}

enum class StoragePoolDataSource {
    KERNEL_INTERFACE,
    COMMAND
}

data class StoragePool(
    val id: String,
    val name: String,
    val kind: StoragePoolKind,
    val state: StoragePoolState,
    val statusMessage: String?,
    val redundancy: String?,
    val sizeBytes: Long?,
    val allocatedBytes: Long?,
    val freeBytes: Long?,
    val devices: List<StoragePoolDevice>,
    val scan: StoragePoolScan?,
    val dataSource: StoragePoolDataSource
)

data class StoragePoolDevice(
    val id: String,
    val name: String,
    val path: String?,
    val role: StoragePoolDeviceRole,
    val state: StoragePoolDeviceState,
    val readErrors: Long?,
    val writeErrors: Long?,
    val checksumErrors: Long?,
    val sizeBytes: Long?
)

data class StoragePoolScan(
    val kind: StoragePoolScanKind,
    val state: StoragePoolScanState,
    val percentComplete: Float?,
    val bytesProcessed: Long?,
    val bytesTotal: Long?,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val estimatedSecondsRemaining: Long?,
    val errors: Long?
)
