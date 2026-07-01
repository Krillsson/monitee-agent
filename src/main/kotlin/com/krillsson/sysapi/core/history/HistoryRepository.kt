package com.krillsson.sysapi.core.history

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.domain.history.SystemHistoryEntry
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.history.db.*
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.measureTimeMillis
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Repository
open class HistoryRepository constructor(
    private val clock: Clock,
    private val dao2: HistorySystemLoadDAO2,
    private val basicDao2: BasicHistorySystemLoadDAO2,
    private val cpuLoadDAO: CpuLoadDAO,
    private val memoryLoadDAO: MemoryLoadDAO,
    private val networkLoadDAO: NetworkLoadDAO,
    private val diskLoadDAO: DiskLoadDAO,
    private val gpuLoadDAO: GpuLoadDAO,
    private val fileSystemLoadDAO: FileSystemLoadDAO,
    private val connectivityDAO: ConnectivityDAO
) {

    val logger by logger()

    open fun get(): List<BasicHistorySystemLoadEntity> {
        return basicDao2.findAll()
    }

    open fun getExtended(): List<SystemHistoryEntry> {
        return getExtendedHistoryLimitedToDates(null, null)
    }

    open fun getExtendedHistoryLimitedToDates(
        fromDate: Instant?,
        toDate: Instant?
    ): List<SystemHistoryEntry> {
        val result = measureTimeMillis {
            if (fromDate == null || toDate == null) {
                dao2.findAll()
            } else {
                dao2.findAllByDateBetween(fromDate, toDate)
            }
        }
        logger.info(
            "Took {} to fetch {} history entries",
            "${result.first.toInt()}ms",
            result.second.size
        )
        return result.second.map { it.asSystemHistoryEntry() }
    }

    open fun record(load: HistorySystemLoad) {
        val entry = SystemHistoryEntry(UUID.randomUUID(), clock.instant(), load)
        logger.trace("Recording history for {}", entry)
        dao2.save(entry.asEntity())
    }

    @Transactional
    open fun purge(olderThan: Long, unit: ChronoUnit) {
        val maxAge = clock.instant().minus(olderThan, unit)
        logger.info("Purging history older than {}", maxAge)
        dao2.deleteAllByDateLessThan(maxAge)
    }

    open fun getHistoryLimitedToDates(
        fromDate: Instant?,
        toDate: Instant?
    ): List<BasicHistorySystemLoadEntity> {
        val result = measureTimeMillis {
            if (fromDate == null || toDate == null) {
                basicDao2.findAll()
            } else {
                basicDao2.findAllByDateBetween(fromDate, toDate)
            }
        }
        logger.info(
            "Took {} to fetch {} history entries",
            "${result.first.toInt()}ms",
            result.second.size
        )
        return result.second
    }

    open fun getBasic(): List<BasicHistorySystemLoadEntity> {
        return getHistoryLimitedToDates(null, null)
    }

    open fun getCpuLoadById(id: UUID): CpuLoad {
        return cpuLoadDAO.findById(id).get().asCpuLoad()
    }

    open fun getMemoryLoadById(id: UUID): MemoryLoad {
        return memoryLoadDAO.findById(id).get().asMemoryLoad()
    }

    open fun getConnectivityById(id: UUID): Connectivity {
        return connectivityDAO.findById(id).get().asConnectivity()
    }

    open fun getNetworkInterfaceLoadsById(id: UUID): List<NetworkInterfaceLoad> {
        return networkLoadDAO.findAllByHistoryId(id).map { it.asNetworkInterfaceLoad() }
    }

    open fun getDiskLoadsById(id: UUID): List<DiskLoad> {
        return diskLoadDAO.findAllByHistoryId(id).map { it.asDiskLoad() }
    }

    open fun getGpuLoadsById(id: UUID): List<com.krillsson.sysapi.core.domain.gpu.GpuLoad> {
        return gpuLoadDAO.findAllByHistoryId(id).map { it.asGpuLoad() }
    }

    open fun getFileSystemLoadsById(id: UUID): List<FileSystemLoad> {
        return fileSystemLoadDAO.findAllByHistoryId(id).map { it.asFileSystemLoad() }
    }
}