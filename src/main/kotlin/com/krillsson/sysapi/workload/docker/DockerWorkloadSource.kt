package com.krillsson.sysapi.workload.docker

import com.krillsson.sysapi.core.domain.docker.displayName
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.Status
import com.krillsson.sysapi.docker.unavailableReason
import com.krillsson.sysapi.workload.PlatformAvailability
import com.krillsson.sysapi.workload.RunStates
import com.krillsson.sysapi.workload.Workload
import com.krillsson.sysapi.workload.WorkloadKind
import com.krillsson.sysapi.workload.WorkloadPlatform
import com.krillsson.sysapi.workload.WorkloadSource
import org.springframework.stereotype.Component

@Component
class DockerWorkloadSource(private val containerService: ContainerService) : WorkloadSource {

    override val platform = WorkloadPlatform.DOCKER
    override val kinds = setOf(WorkloadKind.CONTAINER)

    override fun read(): WorkloadSource.Reading {
        val status = containerService.status
        val reason = status.unavailableReason()
        return WorkloadSource.Reading(
            availability = PlatformAvailability(
                platform = platform,
                available = reason == null,
                isDisabled = status == Status.Disabled,
                reason = reason,
                lastSuccessfulPoll = null
            ),
            workloads = containerService.containers().map { container ->
                Workload(
                    kind = WorkloadKind.CONTAINER,
                    platform = platform,
                    id = container.id,
                    name = container.displayName(),
                    runState = RunStates.docker(container.state),
                    source = container
                )
            }
        )
    }
}
