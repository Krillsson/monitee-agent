package com.krillsson.sysapi.storagepool

fun storagePool(
    id: String = "md:abcd",
    name: String = id.substringAfter(':'),
    state: StoragePoolState = StoragePoolState.ONLINE
) = StoragePool(
    id = id,
    name = name,
    kind = StoragePoolKind.MDADM,
    state = state,
    statusMessage = null,
    redundancy = "raid1",
    sizeBytes = null,
    allocatedBytes = null,
    freeBytes = null,
    devices = emptyList(),
    scan = null,
    dataSource = StoragePoolDataSource.KERNEL_INTERFACE
)
