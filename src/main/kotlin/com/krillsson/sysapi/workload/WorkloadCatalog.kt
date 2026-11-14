package com.krillsson.sysapi.workload

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service

@Service
class WorkloadCatalog(private val sources: List<WorkloadSource>) {

    private val logger by logger()

    private val order = compareBy<Workload> { it.runState != RunState.RUNNING }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

    fun list(kind: WorkloadKind): WorkloadList {
        val readings = sources
            .filter { kind in it.kinds }
            .sortedBy { it.platform }
            .map { it.readOrUnavailable() }
        return WorkloadList(
            workloads = readings.flatMap { it.workloads }.filter { it.kind == kind }.sortedWith(order),
            platforms = readings.map { it.availability }
        )
    }

    private fun WorkloadSource.readOrUnavailable(): WorkloadSource.Reading = runCatching { read() }.getOrElse { error ->
        logger.warn("Failed to list the workloads on {}", platform, error)
        WorkloadSource.Reading(
            availability = PlatformAvailability(
                platform = platform,
                available = false,
                isDisabled = false,
                reason = error.message ?: "Unknown reason Type: ${error::class.simpleName}",
                lastSuccessfulPoll = null
            ),
            workloads = emptyList()
        )
    }
}
