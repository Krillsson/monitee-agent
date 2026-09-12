package com.krillsson.sysapi.core.history.compat

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.history.db.BasicHistorySystemLoadEntity
import java.time.Instant

sealed interface SystemHistoryPoint {

    val timestamp: Instant

    data class Stored(val entity: BasicHistorySystemLoadEntity) : SystemHistoryPoint {
        override val timestamp: Instant = entity.date
    }

    data class Synthesized(
        override val timestamp: Instant,
        val processorMetrics: CpuLoad,
        val memoryMetrics: MemoryLoad,
        val connectivity: Connectivity,
        val networkInterfaceMetrics: List<NetworkInterfaceLoad>,
        val diskMetrics: List<DiskLoad>,
        val fileSystemMetrics: List<FileSystemLoad>,
        val gpuMetrics: List<GpuLoad>
    ) : SystemHistoryPoint
}
