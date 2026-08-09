package com.krillsson.sysapi.core.metrics.defaultimpl

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Ordering
import com.krillsson.sysapi.config.ProcessesConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.processes.Process
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.domain.processes.ProcessSort.*
import com.krillsson.sysapi.core.domain.processes.ProcessesInfo
import com.krillsson.sysapi.core.metrics.ProcessesMetrics
import com.krillsson.sysapi.util.OSProcessComparators
import com.krillsson.sysapi.util.measureTimeMillis
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import oshi.hardware.HardwareAbstractionLayer
import oshi.software.os.OSProcess
import oshi.software.os.OperatingSystem
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
class DefaultProcessesMetrics(
    private val config: YAMLConfigFile,
    private val operatingSystem: OperatingSystem,
    private val hal: HardwareAbstractionLayer,
) : ProcessesMetrics {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ProcessesMetrics::class.java)
    }

    private val priorSnapshotMap: MutableMap<Int, OSProcess> = ConcurrentHashMap()
    private val currentLoad: MutableMap<Int, Double> = ConcurrentHashMap()

    // profiling showed that user and group was expensive in Linux
    private val cachedUserAndGroup: Cache<Int, Pair<String, String>> = CacheBuilder.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build()

    init {
        doIfEnabled {
            updateMap(operatingSystem.processes)
        }
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    fun run() {
        doIfEnabled {
            val processes = operatingSystem.processes
            updateCurrentLoad(processes)
            updateMap(processes)
        }
    }

    override fun getProcessByPid(pid: Int): Optional<Process> {
        val process: OSProcess? = operatingSystem.getProcess(pid)
        process?.let {
            priorSnapshotMap[process.processID] = process
        }
        return Optional.ofNullable(
            process?.asProcess(
                currentLoad.getOrDefault(pid, 0.0),
                hal.memory.total
            )
        )
    }

    private fun comparatorFor(sortBy: ProcessSort): Comparator<OSProcess> = when (sortBy) {
        CPU -> OSProcessComparators.CPU_LOAD_COMPARATOR
        MEMORY -> OSProcessComparators.MEMORY_COMPARATOR
        OLDEST -> OSProcessComparators.OLDEST_COMPARATOR
        NEWEST -> OSProcessComparators.NEWEST_COMPARATOR
        PID -> OSProcessComparators.PID_COMPARATOR
        PARENTPID -> OSProcessComparators.PARENT_PID_COMPARATOR
        NAME -> OSProcessComparators.NAME_COMPARATOR
    }

    private fun sortAndLimit(
        processes: Collection<OSProcess>,
        sortBy: ProcessSort,
        limit: Int
    ): List<OSProcess> {
        val comparator = comparatorFor(sortBy)
        return if (limit <= 0) processes.sortedWith(comparator)
        else Ordering.from(comparator).leastOf(processes, limit)
    }

    override fun processesInfo(sortBy: ProcessSort, limit: Int): ProcessesInfo {
        val tracedValue = measureTimeMillis { sortAndLimit(priorSnapshotMap.values, sortBy, limit) }
        LOGGER.trace(
            "Took {} to sort and limit {} processes",
            "${tracedValue.first.toInt()}ms",
            tracedValue.second.size
        )
        return ProcessesInfo(
            operatingSystem.processId.toLong(),
            operatingSystem.threadCount.toLong(),
            operatingSystem.processCount.toLong(),
            tracedValue.second.map { process ->
                process.asProcess(
                    currentLoad.getOrDefault(process.processID, 0.0),
                    hal.memory.total
                )
            }
        )
    }

    private fun updateCurrentLoad(processes: List<OSProcess>) {
        val cpuCount: Int = hal.processor.logicalProcessorCount

        currentLoad.keys.retainAll(processes.mapTo(mutableSetOf()) { it.processID })
        for (process in processes) {
            currentLoad[process.processID] =
                100.0 * process.getProcessCpuLoadBetweenTicks(priorSnapshotMap[process.processID]) / cpuCount
        }
    }

    private fun updateMap(processes: List<OSProcess>) {
        priorSnapshotMap.clear()
        for (process in processes) {
            priorSnapshotMap[process.processID] = process
        }
    }

    private fun OSProcess.getOrCreateUserAndGroup(pid: Int): Pair<String, String> {
        return cachedUserAndGroup.get(pid) { user to group }
    }

    private fun OSProcess.asProcess(cpuPercent: Double, totalBytes: Long): Process {
        val (user, group) = getOrCreateUserAndGroup(processID)
        return Process(
            name,
            path,
            commandLine,
            user,
            userID,
            group,
            groupID,
            state,
            processID,
            parentProcessID,
            threadCount,
            priority,
            virtualSize,
            privateResidentMemory,
            100.0 * privateResidentMemory / totalBytes,
            kernelTime,
            userTime,
            upTime,
            cpuPercent,
            startTime,
            bytesRead,
            bytesWritten
        )
    }

    private fun doIfEnabled(action: () -> Unit) {
        if (config.processes.enabled) {
            action()
        }
    }
}