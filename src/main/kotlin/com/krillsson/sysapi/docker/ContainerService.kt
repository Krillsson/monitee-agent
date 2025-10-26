package com.krillsson.sysapi.docker

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.krillsson.sysapi.config.DockerConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.docker.Command
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.docker.ContainerMetricsHistoryEntry
import com.krillsson.sysapi.graphql.domain.DockerLogMessageConnection
import com.krillsson.sysapi.graphql.domain.DockerLogMessageEdge
import com.krillsson.sysapi.graphql.domain.PageInfo
import com.krillsson.sysapi.util.decodeAsInstantCursor
import com.krillsson.sysapi.util.encodeAsCursor
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.*

@Service
class ContainerService(
    private val dockerClient: DockerClient,
    private val config: YAMLConfigFile,
    private val containersHistoryRepository: ContainersHistoryRepository,
) {
    private val logger by logger()

    private val dockerConfiguration: DockerConfiguration = config.docker
    private val cacheConfiguration = config.docker.cache
    val status = checkAvailability()


    private val containersCache: Supplier<List<Container>> = Suppliers.memoizeWithExpiration(
        {
            if (status == Status.Available) {
                dockerClient.listContainers()
            } else {
                emptyList()
            }
        },
        cacheConfiguration.duration, cacheConfiguration.unit
    )

    private val containerStatsCache: LoadingCache<String, Optional<ContainerMetrics>> = CacheBuilder.newBuilder()
        .expireAfterWrite(cacheConfiguration.duration, cacheConfiguration.unit)
        .build(object : CacheLoader<String, Optional<ContainerMetrics>>() {
            @Throws(Exception::class)
            override fun load(id: String): Optional<ContainerMetrics> {
                return if (status == Status.Available) {
                    dockerClient.containerStatistics(id)?.let { Optional.of(it) } ?: Optional.empty()
                } else {
                    Optional.empty()
                }
            }
        })

    fun containers(): List<Container> {
        return when {
            status != Status.Available -> emptyList()
            !cacheConfiguration.enabled -> dockerClient.listContainers()
            else -> containersCache.get()
        }
    }

    fun container(id: String): Container? {
        return containersWithIds(listOf(id)).firstOrNull()
    }

    fun containersWithIds(ids: List<String>): List<Container> {
        return when {
            status != Status.Available -> emptyList()
            !cacheConfiguration.enabled -> dockerClient.listContainers(ids)
            else -> containersCache.get().filter { ids.contains(it.id) }
        }
    }

    fun containerMetricsHistoryBetweenTimestamps(
        containerId: String,
        from: Instant,
        to: Instant
    ): List<ContainerMetricsHistoryEntry> {
        return containersHistoryRepository.getHistoryLimitedToDates(
            containerId,
            from,
            to
        )
    }

    fun statsForContainer(id: String): ContainerMetrics? {
        return when {
            status != Status.Available -> null
            !cacheConfiguration.enabled -> dockerClient.containerStatistics(id)
            else -> containerStatsCache.get(id).orElse(null)
        }
    }

    fun containerStats(): List<ContainerMetrics> {
        return when {
            status != Status.Available -> emptyList()
            else -> containers().mapNotNull {
                statsForContainer(it.id)
            }
        }
    }

    fun performCommandWithContainer(command: Command): CommandResult {
        if (status == Status.Available) {
            return dockerClient.performCommandWithContainer(command)
        }
        return CommandResult.Unavailable
    }

    fun readLogsForContainer(containerId: String, from: Instant?, to: Instant?): ReadLogsCommandResult {
        if (status == Status.Available) {
            return try {
                ReadLogsCommandResult.Success(dockerClient.readLogsForContainer(containerId, from, to))
            } catch (interruptedException: InterruptedException) {
                ReadLogsCommandResult.TimedOut(DockerClient.READ_LOGS_COMMAND_TIMEOUT_SEC)
            } catch (e: RuntimeException) {
                ReadLogsCommandResult.Failed(e)
            }
        }
        return ReadLogsCommandResult.Unavailable
    }

    fun tailContainerLogs(
        containerId: String,
        startPosition: String?,
        reverse: Boolean?
    ): Flux<DockerLogMessage> {
        val historicalLines = if (startPosition != null) {
            val timestamp = startPosition.decodeAsInstantCursor()
            dockerClient.readLogLinesForContainer(
                containerId,
                from = timestamp,
                to = Instant.now()
            )
                .let { list -> if (reverse == true) list.sortedByDescending { it.timestamp } else list }
                .filter { it.timestamp.isAfter(timestamp) }
        } else {
            emptyList()
        }

        return dockerClient
            .tailLogsForContainer(containerId)
            .startWith(Flux.fromIterable(historicalLines))
    }

    fun openContainerLogsConnection(
        containerId: String,
        after: String?,
        before: String?,
        first: Int?,
        last: Int?,
        reverse: Boolean?
    ): DockerLogMessageConnection {

        val latestTimestamp = dockerClient.readLogLinesForContainer(containerId, tail = 1).firstOrNull()?.timestamp
        val firstTimestamp = dockerClient.readFirstLogLineForContainer(containerId)?.timestamp
        logger.debug("Latest timestamp: ${latestTimestamp.toString()} firstTimeStamp: ${firstTimestamp.toString()}")

        val (fromTimestamp, toTimestamp) = if (reverse == true) {
            before?.decodeAsInstantCursor() to after?.decodeAsInstantCursor()
        } else {
            after?.decodeAsInstantCursor() to before?.decodeAsInstantCursor()
        }
        val pageSize = first ?: last ?: 10

        val filteredLogs = when {
            reverse == true && fromTimestamp == null && toTimestamp == null -> {
                dockerClient.readLogLinesForContainer(containerId, tail = pageSize)
                    .sortedByDescending { it.timestamp }
            }

            else -> {
                dockerClient.readLogLinesForContainer(
                    containerId = containerId,
                    from = fromTimestamp,
                    to = toTimestamp
                )
            }
        }

        val sortedLogs = if (reverse == true) {
            filteredLogs.sortedByDescending { it.timestamp }
        } else {
            filteredLogs
        }

        val paginatedLogs = when {
            first != null -> sortedLogs.take(first)
            last != null -> sortedLogs.takeLast(last)
            else -> sortedLogs.take(pageSize)
        }

        val (hasNext, hasPrevious) = when {
            reverse == true -> {
                paginatedLogs.lastOrNull()?.timestamp?.let { lastInSet ->
                    firstTimestamp?.isBefore(lastInSet)
                } to paginatedLogs.firstOrNull()?.timestamp?.let { firstInSet ->
                    latestTimestamp?.isAfter(firstInSet)
                }
            }

            else -> {
                paginatedLogs.lastOrNull()?.timestamp?.let { lastInSet ->
                    latestTimestamp?.isAfter(lastInSet)
                } to paginatedLogs.firstOrNull()?.timestamp?.let { firstInSet ->
                    firstTimestamp?.isBefore(firstInSet)
                }
            }
        }

        val edges = paginatedLogs.map {
            DockerLogMessageEdge(
                cursor = it.timestamp.encodeAsCursor(),
                node = it
            )
        }

        val pageInfo = PageInfo(
            hasNextPage = hasNext ?: false,
            hasPreviousPage = hasPrevious ?: false,
            startCursor = edges.firstOrNull()?.cursor,
            endCursor = edges.lastOrNull()?.cursor
        )
        logger.debug("Container: $containerId, after: $after, before: $before, first: $first, last: $last, reverse: $reverse")
        logger.debug("Returning info: $pageInfo and ${edges.size} edges")
        return DockerLogMessageConnection(
            edges = edges,
            pageInfo = pageInfo
        )
    }


    private fun checkAvailability(): Status {
        return when {
            !dockerConfiguration.enabled -> {
                Status.Disabled
            }

            else -> {
                when (val result = dockerClient.checkAvailability()) {
                    is DockerClient.PingResult.Fail -> {
                        logger.error("Docker is unavailable because of an error ${result.throwable.message}")
                        Status.Unavailable(result.throwable)
                    }

                    DockerClient.PingResult.Success -> Status.Available
                }
            }
        }
    }
}