package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.docker.ContainerMetricsHistoryEntry
import com.krillsson.sysapi.core.domain.docker.State
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.ReadLogsCommandResult
import com.krillsson.sysapi.graphql.domain.*
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.time.OffsetDateTime

@Controller
@SchemaMapping(typeName = "DockerAvailable")
class DockerResolver(val containerService: ContainerService) {

    @SchemaMapping
    fun containers() = containerService.containers()
    @SchemaMapping
    fun container(@Argument id: String) = containerService.container(id)

    @SchemaMapping
    fun runningContainers() =
        containerService.containers().filter { it.state == State.RUNNING }

    @SchemaMapping
    fun readLogsForContainer(
        @Argument
        containerId: String,
        @Argument
        from: OffsetDateTime?,
        @Argument
        to: OffsetDateTime?
    ): ReadLogsForContainerOutput {
        return when (val result =
            containerService.readLogsForContainer(containerId, from?.toInstant(), to?.toInstant())) {
            is ReadLogsCommandResult.Success -> ReadLogsForContainerOutputSucceeded(result.lines)
            is ReadLogsCommandResult.Failed -> ReadLogsForContainerOutputFailed(
                result.error.message ?: result.error.toString()
            )

            is ReadLogsCommandResult.TimedOut -> ReadLogsForContainerOutputFailed("Operation timed out after ${result.timeoutSeconds} seconds")
            ReadLogsCommandResult.Unavailable -> ReadLogsForContainerOutputFailed("Docker is not available")
        }
    }

    @SchemaMapping
    fun readLogsForContainerBetweenTimestamps(
        @Argument
        containerId: String,
        @Argument
        from: Instant?,
        @Argument
        to: Instant?
    ): ReadLogsForContainerOutput {
        return when (val result = containerService.readLogsForContainer(containerId, from, to)) {
            is ReadLogsCommandResult.Success -> ReadLogsForContainerOutputSucceeded(result.lines)
            is ReadLogsCommandResult.Failed -> ReadLogsForContainerOutputFailed(
                result.error.message ?: result.error.toString()
            )

            is ReadLogsCommandResult.TimedOut -> ReadLogsForContainerOutputFailed("Operation timed out after ${result.timeoutSeconds} seconds")
            ReadLogsCommandResult.Unavailable -> ReadLogsForContainerOutputFailed("Docker is not available")
        }
    }

    @SchemaMapping
    fun openDockerLogMessageConnection(
        @Argument containerId: String,
        @Argument after: String?,
        @Argument before: String?,
        @Argument first: Int?,
        @Argument last: Int?,
        @Argument reverse: Boolean?
    ): DockerLogMessageConnection {
        return containerService.openContainerLogsConnection(
            containerId = containerId,
            after = after,
            before = before,
            first = first,
            last = last,
            reverse = reverse
        )
    }

    @SchemaMapping
    fun metricsForContainer(@Argument containerId: String): ContainerMetrics? {
        return containerService.statsForContainer(containerId)
    }

    @SchemaMapping
    fun containerMetricsHistoryBetweenTimestamps(
        @Argument containerId: String,
        @Argument from: Instant,
        @Argument to: Instant
    ): List<ContainerMetricsHistoryEntry> {
        return containerService.containerMetricsHistoryBetweenTimestamps(
            containerId, from, to
        )
    }
}