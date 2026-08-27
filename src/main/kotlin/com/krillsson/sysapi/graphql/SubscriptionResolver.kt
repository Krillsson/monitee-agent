package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.BuildConfig
import com.krillsson.sysapi.core.connectivity.InternetServicesCheckService
import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.domain.disk.DiskLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.domain.memory.MemoryLoad
import com.krillsson.sysapi.core.domain.network.NetworkInterfaceLoad
import com.krillsson.sysapi.core.metrics.Metrics
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.filebrowser.FileOperation
import com.krillsson.sysapi.filebrowser.FileOperationService
import com.krillsson.sysapi.docker.ContainerBatchUpdateJobs
import com.krillsson.sysapi.docker.ContainerUpdateJobs
import com.krillsson.sysapi.graphql.domain.DockerContainerBatchUpdateEvent
import com.krillsson.sysapi.graphql.domain.DockerContainerUpdateEvent
import com.krillsson.sysapi.graphql.domain.InternetService
import com.krillsson.sysapi.graphql.domain.Meta
import com.krillsson.sysapi.logaccess.file.LogFileService
import com.krillsson.sysapi.serverid.ServerIdService
import com.krillsson.sysapi.systemd.SystemDaemonManager
import com.krillsson.sysapi.ups.UpsService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.stereotype.Controller
import oshi.software.os.OperatingSystem
import reactor.core.publisher.Flux
import java.util.UUID

@Controller
class SubscriptionResolver(
    val metrics: Metrics,
    val operatingSystem: OperatingSystem,
    val logFileService: LogFileService,
    val containerService: ContainerService,
    val containerUpdateJobs: ContainerUpdateJobs,
    val containerBatchUpdateJobs: ContainerBatchUpdateJobs,
    val serverIdService: ServerIdService,
    val upsService: UpsService,
    val systemDaemonManager: SystemDaemonManager,
    val fileOperations: FileOperationService
) {
    @SubscriptionMapping
    fun fileOperationProgress(@Argument id: String): Flux<FileOperation> = fileOperations.events(id)

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
    fun upsMetricsById(@Argument id: String) = upsService.upsDevicesMetricsById(id)

    @SubscriptionMapping
    fun networkInterfaceMetrics(): Flux<List<NetworkInterfaceLoad>> {
        return metrics.networkMetrics().networkInterfaceLoadEvents()
    }

    @SubscriptionMapping
    fun internetServiceAvailabilities(): Flux<List<InternetService>> {
        return metrics.networkMetrics().internetServiceAvailabilitiesEvents().map { list ->
            list.map { item ->
                when (item) {
                    is InternetServicesCheckService.InternetServiceAvailability.Available -> InternetService(
                        item.id,
                        item.name,
                        item.address,
                        item.port,
                        true,
                        null,
                        item.latencyMs
                    )

                    is InternetServicesCheckService.InternetServiceAvailability.Unavailable -> InternetService(
                        item.id,
                        item.name,
                        item.address,
                        item.port,
                        false,
                        item.message,
                        -1
                    )
                }
            }
        }
    }

    @SubscriptionMapping
    fun networkInterfaceMetricsById(@Argument id: String) = metrics.networkMetrics().networkInterfaceLoadEventsById(id)

    @SubscriptionMapping
    fun gpuMetrics(): Flux<List<GpuLoad>> {
        return metrics.gpuMetrics().gpuLoadEvents()
    }

    @SubscriptionMapping
    fun gpuMetricsById(@Argument id: String) = metrics.gpuMetrics().gpuLoadEventsById(id)

    @SubscriptionMapping
    fun meta(): Flux<Meta> = Flux.just(
        Meta(
            version = BuildConfig.APP_VERSION,
            buildDate = BuildConfig.BUILD_TIME.toString(),
            processId = operatingSystem.processId,
            serverId = serverIdService.serverId,
            endpoints = emptyList(),
        )
    )

    @SubscriptionMapping
    fun tailLogFile(@Argument path: String, @Argument startPosition: String?, @Argument reverse: Boolean?) =
        logFileService.tailLogFile(path, startPosition, reverse)

    @SubscriptionMapping
    fun tailContainerLogs(@Argument containerId: String, @Argument after: String?, @Argument reverse: Boolean?) =
        containerService.tailContainerLogs(containerId, after, reverse)

    @SubscriptionMapping
    fun tailJournalLogs(@Argument serviceName: String, @Argument after: String?, @Argument reverse: Boolean?) =
        systemDaemonManager.openAndTailJournal(serviceName, after, reverse)

    @SubscriptionMapping
    fun dockerContainerUpdate(@Argument jobId: UUID): Flux<DockerContainerUpdateEvent> =
        containerUpdateJobs.events(jobId)

    @SubscriptionMapping
    fun dockerContainerBatchUpdate(@Argument batchJobId: UUID): Flux<DockerContainerBatchUpdateEvent> =
        containerBatchUpdateJobs.events(batchJobId)
}