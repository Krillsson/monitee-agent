package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.db.BasicHistorySystemLoadEntity
import com.krillsson.sysapi.util.toOffsetDateTime
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.time.OffsetDateTime

@Controller
@SchemaMapping(typeName = "SystemMetricsHistoryEntry")
class SystemMetricsHistoryEntryResolver(val historyRepository: HistoryRepository) {

    @SchemaMapping
    fun dateTime(historyEntry: BasicHistorySystemLoadEntity): OffsetDateTime {
        return historyEntry.date.toOffsetDateTime()
    }

    @SchemaMapping
    fun timestamp(historyEntry: BasicHistorySystemLoadEntity): Instant {
        return historyEntry.date
    }

    @SchemaMapping
    fun processorMetrics(historyEntry: BasicHistorySystemLoadEntity): CpuLoad {
        return historyRepository.getCpuLoadById(historyEntry.id)
    }

    @SchemaMapping
    fun diskMetrics(historyEntry: BasicHistorySystemLoadEntity): List<DiskLoad> {
        return historyRepository.getDiskLoadsById(historyEntry.id)
    }

    @SchemaMapping
    fun fileSystemMetrics(historyEntry: BasicHistorySystemLoadEntity): List<FileSystemLoad> {
        return historyRepository.getFileSystemLoadsById(historyEntry.id)
    }

    @SchemaMapping
    fun networkInterfaceMetrics(historyEntry: BasicHistorySystemLoadEntity): List<NetworkInterfaceLoad> {
        return historyRepository.getNetworkInterfaceLoadsById(historyEntry.id)
    }

    @SchemaMapping
    fun gpuMetrics(historyEntry: BasicHistorySystemLoadEntity): List<GpuLoad> {
        return historyRepository.getGpuLoadsById(historyEntry.id)
    }

    @SchemaMapping
    fun connectivity(historyEntry: BasicHistorySystemLoadEntity): Connectivity {
        return historyRepository.getConnectivityById(historyEntry.id)
    }

    @SchemaMapping
    fun memoryMetrics(historyEntry: BasicHistorySystemLoadEntity): MemoryLoad {
        return historyRepository.getMemoryLoadById(historyEntry.id)
    }
}