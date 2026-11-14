package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.unraid.UnraidClient.Result
import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommandConfirmation
import com.krillsson.sysapi.workload.PowerCommandExecutor
import com.krillsson.sysapi.workload.PowerCommandExecutor.Admission
import com.krillsson.sysapi.workload.PowerCommandProgress
import com.krillsson.sysapi.workload.WorkloadPlatform
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class UnraidPowerCommandExecutor(
    configuration: YAMLConfigFile,
    private val client: UnraidClient,
    private val vmService: UnraidVmService,
    private val confirmation: PowerCommandConfirmation
) : PowerCommandExecutor {
    companion object {
        val CONFIRMATION_TIMEOUT: Duration = Duration.ofSeconds(30)
    }

    private val config = configuration.unraid

    override val platform: WorkloadPlatform = WorkloadPlatform.UNRAID

    override fun admit(id: String, command: PowerCommand): Admission {
        val status = vmService.status()
        val vmsUnavailableReason = vmService.snapshot()?.vmsUnavailableReason
        val vm = vmService.vm(id)
        return when {
            status is UnraidVmService.Status.Disabled -> Admission.Refused("Unraid is not enabled in configuration.yml")
            status is UnraidVmService.Status.Unavailable -> Admission.Refused(status.reason)
            vmsUnavailableReason != null -> Admission.Refused(vmsUnavailableReason)
            vm == null -> Admission.Refused("There is no VM with id $id")
            else -> UnraidVmCommands.refusal(vm, command)?.let { Admission.Refused(it) } ?: Admission.Admitted(vm.name)
        }
    }

    override fun run(id: String, command: PowerCommand, progress: PowerCommandProgress) {
        when (val result = client.vmCommand(UnraidVmCommands.mutation(command), id)) {
            is Result.Ok -> when (result.data) {
                true -> confirm(id, command, progress)
                false -> {
                    vmService.poll()
                    progress.failed("Unraid reported that the VM did not take the command")
                }
            }

            is Result.Failure -> {
                vmService.poll()
                progress.failed(
                    UnraidConverter.commandFailureReason(result, config),
                    canForceStop = UnraidVmCommands.canForceStop(command, result)
                )
            }
        }
    }

    private fun confirm(id: String, command: PowerCommand, progress: PowerCommandProgress) {
        confirmation.await(
            CONFIRMATION_TIMEOUT,
            reached = {
                vmService.poll()
                UnraidVmCommands.reached(command, vmService.vm(id)?.state)
            }
        ) { reached ->
            when {
                reached -> progress.succeeded()
                else -> progress.failed(
                    "Unraid reported success, but the VM is ${UnraidVmCommands.describe(vmService.vm(id)?.state)}"
                )
            }
        }
    }
}
