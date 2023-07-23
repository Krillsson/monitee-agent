package com.krillsson.sysapi.core.history

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.drives.DriveLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.history.HistorySystemLoad
import com.krillsson.sysapi.core.domain.history.SystemHistoryEntry
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.history.db.*
import com.krillsson.sysapi.util.Clock
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.util.measureTimeMillis
import io.dropwizard.hibernate.UnitOfWork
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

open class HistoryRepository constructor(
    private val clock: Clock,
    private val dao: HistorySystemLoadDAO,
    private val basicDao: BasicHistorySystemLoadDAO,
    private val cpuLoadDAO: CpuLoadDAO,
    private val memoryLoadDAO: MemoryLoadDAO,
    private val networkLoadDAO: NetworkLoadDAO,
    private val driveLoadDAO: DriveLoadDAO,
    private val diskLoadDAO: DiskLoadDAO,
    private val fileSystemLoadDAO: FileSystemLoadDAO,
    private val connectivityDAO: ConnectivityDAO
) {

    val logger by logger()

    @UnitOfWork(readOnly = true)
    open fun get(): List<BasicHistorySystemLoadEntity> {
        return basicDao.findAll()
    }

    @UnitOfWork(readOnly = true)
    open fun getExtended(): List<SystemHistoryEntry> {
        return getExtendedHistoryLimitedToDates(null, null)
    }

    @UnitOfWork(readOnly = true)
    open fun getExtendedHistoryLimitedToDates(
        fromDate: OffsetDateTime?,
        toDate: OffsetDateTime?
    ): List<SystemHistoryEntry> {
        val result = measureTimeMillis {
            if (fromDate == null || toDate == null) {
                dao.findAll()
            } else {
                dao.findAllBetween(fromDate, toDate)
            }
        }
        logger.info(
            "Took {} to fetch {} history entries",
            "${result.first.toInt()}ms",
            result.second.size
        )
        return result.second.map { it.asSystemHistoryEntry() }
    }

    @UnitOfWork
    open fun record(load: HistorySystemLoad) {
        val entry = SystemHistoryEntry(UUID.randomUUID(), clock.now(), load)
        logger.trace("Recording history for {}", entry)
        dao.insert(entry.asEntity())
    }

    @UnitOfWork
    open fun purge(olderThan: Long, unit: ChronoUnit) {
        val maxAge = clock.now().minus(olderThan, unit)
        logger.info("Purging history older than {}", maxAge)
        dao.purge(maxAge)
    }

    @UnitOfWork(readOnly = true)
    open fun getHistoryLimitedToDates(
        fromDate: OffsetDateTime?,
        toDate: OffsetDateTime?
    ): List<BasicHistorySystemLoadEntity> {
        val result = measureTimeMillis {
            if (fromDate == null || toDate == null) {
                basicDao.findAll()
            } else {
                basicDao.findAllBetween(fromDate, toDate)
            }
        }
        logger.info(
            "Took {} to fetch {} history entries",
            "${result.first.toInt()}ms",
            result.second.size
        )
        return result.second
    }

    @UnitOfWork(readOnly = true)
    open fun getBasic(): List<BasicHistorySystemLoadEntity> {
        return getHistoryLimitedToDates(null, null)
    }

    @UnitOfWork(readOnly = true)
    open fun getCpuLoadById(id: UUID): CpuLoad {
        return cpuLoadDAO.findById(id).asCpuLoad()
    }

    @UnitOfWork(readOnly = true)
    open fun getMemoryLoadById(id: UUID): MemoryLoad {
        return memoryLoadDAO.findById(id).asMemoryLoad()
    }

    @UnitOfWork(readOnly = true)
    open fun getConnectivityById(id: UUID): Connectivity {
        return connectivityDAO.findById(id).asConnectivity()
    }

    @UnitOfWork(readOnly = true)
    open fun getNetworkInterfaceLoadsById(id: UUID): List<NetworkInterfaceLoad> {
        return networkLoadDAO.findById(id).map { it.asNetworkInterfaceLoad() }
    }

    @UnitOfWork(readOnly = true)
    open fun getDriveLoadsById(id: UUID): List<DriveLoad> {
        return driveLoadDAO.findById(id).map { it.asDriveLoad() }
    }

    @UnitOfWork(readOnly = true)
    open fun getDiskLoadsById(id: UUID): List<DiskLoad> {
        return diskLoadDAO.findById(id).map { it.asDiskLoad() }
    }

    @UnitOfWork(readOnly = true)
    open fun getFileSystemLoadsById(id: UUID): List<FileSystemLoad> {
        return fileSystemLoadDAO.findById(id).map { it.asFileSystemLoad() }
    }

}