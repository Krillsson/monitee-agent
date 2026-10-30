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
            MonitoredValue.EnumValue(stateOf(pools, monitoredItemId))

        private fun stateOf(pools: List<StoragePool>, monitoredItemId: String?): StoragePoolState =
            pools.firstOrNull { it.id == monitoredItemId }?.state ?: StoragePoolState.UNAVAIL
    }

    override val type: Type = Type.STORAGE_POOL_HEALTH

    override fun selectValue(event: MonitorInput): MonitoredValue.EnumValue<StoragePoolState>? {
        return value(event.storagePools, config.monitoredItemId)
    }

    override val entries: EnumEntries<StoragePoolState> = StoragePoolState.entries

    // An UNKNOWN reading means the pool couldn't be assessed, not that it's unhealthy - don't alert on it
    override fun isPastThreshold(
        value: MonitoredValue.EnumValue<StoragePoolState>,
        threshold: MonitoredValue.EnumValue<StoragePoolState>
    ): Boolean {
        return when (value.value) {
            StoragePoolState.UNKNOWN -> false
            else -> super.isPastThreshold(value, threshold)
        }
    }
}
