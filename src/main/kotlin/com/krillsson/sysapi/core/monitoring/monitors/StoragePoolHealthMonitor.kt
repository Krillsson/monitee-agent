package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.storagepool.StoragePool
import com.krillsson.sysapi.storagepool.StoragePoolState
import java.util.*
import kotlin.enums.EnumEntries

class StoragePoolHealthMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.EnumValue<StoragePoolState>>
) : EnumMonitorBase<StoragePoolState>(id, config) {

    companion object {
        fun value(pools: List<StoragePool>, monitoredItemId: String?) =
            pools.firstOrNull { it.id == monitoredItemId }?.state?.let { MonitoredValue.EnumValue(it) }
    }

    override val type: Type = Type.STORAGE_POOL_HEALTH

    override fun selectValue(event: MonitorInput): MonitoredValue.EnumValue<StoragePoolState>? {
        return value(event.storagePools, config.monitoredItemId)
    }

    override val entries: EnumEntries<StoragePoolState> = StoragePoolState.entries

    // An UNKNOWN reading means the pool couldn't be assessed, not that it's unhealthy - don't alert on it
    override fun isPastThreshold(value: MonitoredValue.EnumValue<StoragePoolState>): Boolean {
        if (value.value == StoragePoolState.UNKNOWN) return false
        return super.isPastThreshold(value)
    }
}
