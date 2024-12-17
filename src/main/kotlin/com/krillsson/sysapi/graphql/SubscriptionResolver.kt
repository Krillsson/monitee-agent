package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.docker.ContainerManager
import com.krillsson.sysapi.graphql.domain.Meta
import com.krillsson.sysapi.logaccess.file.LogFileService
import com.krillsson.sysapi.systemd.SystemDaemonManager
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux

@Controller
class SubscriptionResolver(
    val metrics: Metrics,
    val meta: Meta,
    val logFileService: LogFileService,
    val containerManager: ContainerManager,
    val systemDaemonManager: SystemDaemonManager
) {
    @SubscriptionMapping
    fun processorMetrics(): Flux<CpuLoad> {
        return metrics.cpuMetrics().cpuLoadEvents()
    }

    @SubscriptionMapping
    fun memoryMetrics(): Flux<MemoryLoad> {
        return metrics.memoryMetrics().memoryLoadEvents()
    }

    @SubscriptionMapping
    fun diskMetrics(): Flux<List<DiskLoad>> {
        return metrics.diskMetrics().diskLoadEvents()
    }

    @SubscriptionMapping
    fun fileSystemMetricsById(@Argument id: String) = metrics.fileSystemMetrics().fileSystemEventsById(id)

    @SubscriptionMapping
    fun fileSystemMetrics(): Flux<List<FileSystemLoad>> {
        return metrics.fileSystemMetrics().fileSystemEvents()
    }

    @SubscriptionMapping
    fun diskMetricsById(@Argument id: String) = metrics.diskMetrics().diskLoadEventsByName(id)

    @SubscriptionMapping
    fun networkInterfaceMetrics(): Flux<List<NetworkInterfaceLoad>> {
        return metrics.networkMetrics().networkInterfaceLoadEvents()
    }

    @SubscriptionMapping
    fun networkInterfaceMetricsById(@Argument id: String) = metrics.networkMetrics().networkInterfaceLoadEventsById(id)

    @SubscriptionMapping
    fun meta(): Flux<Meta> = Flux.just(meta)

    @SubscriptionMapping
    fun tailLogFile(@Argument path: String, @Argument startPosition: String?, @Argument reverse: Boolean?) = logFileService.tailLogFile(path, startPosition, reverse)

    @SubscriptionMapping
    fun tailContainerLogs(@Argument containerId: String, @Argument after: String?, @Argument reverse: Boolean) = containerManager.tailContainerLogs(containerId, after, reverse)

    @SubscriptionMapping
    fun tailJournalLogs(@Argument serviceName: String, @Argument after: String?) = systemDaemonManager.openAndTailJournal(serviceName, after)
}