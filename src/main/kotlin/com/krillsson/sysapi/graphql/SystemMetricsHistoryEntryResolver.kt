package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.history.HistoryRepository
import com.krillsson.sysapi.core.history.compat.SystemHistoryPoint
import com.krillsson.sysapi.util.toOffsetDateTime
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.time.OffsetDateTime

@Controller
@SchemaMapping(typeName = "SystemMetricsHistoryEntry")
class SystemMetricsHistoryEntryResolver(val historyRepository: HistoryRepository) {

    @SchemaMapping
    fun date(point: SystemHistoryPoint): String = point.timestamp.toString()

    @SchemaMapping
    fun dateTime(point: SystemHistoryPoint): OffsetDateTime = point.timestamp.toOffsetDateTime()

    @SchemaMapping
    fun timestamp(point: SystemHistoryPoint): Instant = point.timestamp

    @SchemaMapping
    fun processorMetrics(point: SystemHistoryPoint): CpuLoad = when (point) {
        is SystemHistoryPoint.Stored -> historyRepository.getCpuLoadById(point.entity.id)
        is SystemHistoryPoint.Synthesized -> point.processorMetrics
    }

    @SchemaMapping
    fun diskMetrics(point: SystemHistoryPoint): List<DiskLoad> = when (point) {
        is SystemHistoryPoint.Stored -> historyRepository.getDiskLoadsById(point.entity.id)
        is SystemHistoryPoint.Synthesized -> point.diskMetrics
    }

    @SchemaMapping
    fun fileSystemMetrics(point: SystemHistoryPoint): List<FileSystemLoad> = when (point) {
        is SystemHistoryPoint.Stored -> historyRepository.getFileSystemLoadsById(point.entity.id)
        is SystemHistoryPoint.Synthesized -> point.fileSystemMetrics
    }

    @SchemaMapping
    fun networkInterfaceMetrics(point: SystemHistoryPoint): List<NetworkInterfaceLoad> = when (point) {
        is SystemHistoryPoint.Stored -> historyRepository.getNetworkInterfaceLoadsById(point.entity.id)
        is SystemHistoryPoint.Synthesized -> point.networkInterfaceMetrics
    }

    @SchemaMapping
    fun gpuMetrics(point: SystemHistoryPoint): List<GpuLoad> = when (point) {
        is SystemHistoryPoint.Stored -> historyRepository.getGpuLoadsById(point.entity.id)
        is SystemHistoryPoint.Synthesized -> point.gpuMetrics
    }

    @SchemaMapping
    fun connectivity(point: SystemHistoryPoint): Connectivity = when (point) {
        is SystemHistoryPoint.Stored -> historyRepository.getConnectivityById(point.entity.id)
        is SystemHistoryPoint.Synthesized -> point.connectivity
    }

    @SchemaMapping
    fun memoryMetrics(point: SystemHistoryPoint): MemoryLoad = when (point) {
        is SystemHistoryPoint.Stored -> historyRepository.getMemoryLoadById(point.entity.id)
        is SystemHistoryPoint.Synthesized -> point.memoryMetrics
    }
}
