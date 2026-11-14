package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.Command
import com.krillsson.sysapi.core.domain.docker.displayName
import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommandConfirmation
import com.krillsson.sysapi.workload.PowerCommandExecutor
import com.krillsson.sysapi.workload.PowerCommandExecutor.Admission
import com.krillsson.sysapi.workload.PowerCommandProgress
import com.krillsson.sysapi.workload.WorkloadPlatform
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class DockerPowerCommandExecutor(
    private val containerService: ContainerService,
    private val confirmation: PowerCommandConfirmation
) : PowerCommandExecutor {
    companion object {
        val CONFIRMATION_TIMEOUT: Duration = Duration.ofSeconds(10)
    }

    override val platform: WorkloadPlatform = WorkloadPlatform.DOCKER

    override fun admit(id: String, command: PowerCommand): Admission {
        val unavailableReason = containerService.status.unavailableReason()
        val container = containerService.container(id)
        return when {
            unavailableReason != null -> Admission.Refused(unavailableReason)
            container == null -> Admission.Refused("There is no container with id $id")
            else -> DockerPowerCommands.refusal(container, command)?.let { Admission.Refused(it) }
                ?: Admission.Admitted(container.displayName())
        }
    }

    override fun run(id: String, command: PowerCommand, progress: PowerCommandProgress) {
        val result = when (val type = DockerPowerCommands.commandType(command)) {
            null -> containerService.killContainer(id)
            else -> containerService.performCommandWithContainer(Command(id, type))
        }
        when (result) {
            CommandResult.Success -> confirm(id, command, progress)
            CommandResult.Unavailable -> progress.failed("Docker is unavailable")
            is CommandResult.Failed -> {
                containerService.invalidateContainersCache()
                progress.failed(result.error.message?.trim()?.takeIf { it.isNotEmpty() } ?: result.error::class.java.simpleName)
            }
        }
    }

    private fun confirm(id: String, command: PowerCommand, progress: PowerCommandProgress) {
        confirmation.await(
            CONFIRMATION_TIMEOUT,
            reached = {
                containerService.invalidateContainersCache()
                DockerPowerCommands.reached(command, containerService.container(id)?.state)
            }
        ) { reached ->
            when {
                reached -> progress.succeeded()
                else -> progress.failed(
                    "Docker reported success, but the container is ${DockerPowerCommands.describe(containerService.container(id)?.state)}"
                )
            }
        }
    }
}
