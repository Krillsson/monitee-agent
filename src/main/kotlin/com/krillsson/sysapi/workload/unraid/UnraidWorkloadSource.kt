package com.krillsson.sysapi.workload.unraid

import com.krillsson.sysapi.unraid.UnraidVm
import com.krillsson.sysapi.unraid.UnraidVmService
import com.krillsson.sysapi.workload.PlatformAvailability
import com.krillsson.sysapi.workload.RunStates
import com.krillsson.sysapi.workload.Workload
import com.krillsson.sysapi.workload.WorkloadKind
import com.krillsson.sysapi.workload.WorkloadPlatform
import com.krillsson.sysapi.workload.WorkloadSource
import org.springframework.stereotype.Component

@Component
class UnraidWorkloadSource(private val unraidVmService: UnraidVmService) : WorkloadSource {

    companion object {
        const val DISABLED_REASON = "Unraid support is currently disabled. You can change this in the configuration.yml"
    }

    override val platform = WorkloadPlatform.UNRAID
    override val kinds = setOf(WorkloadKind.VIRTUAL_MACHINE)

    override fun read(): WorkloadSource.Reading {
        val status = unraidVmService.status()
        val snapshot = unraidVmService.snapshot()
        return WorkloadSource.Reading(
            availability = PlatformAvailability(
                platform = platform,
                available = snapshot != null,
                isDisabled = status == UnraidVmService.Status.Disabled,
                reason = when {
                    snapshot != null -> snapshot.vmsUnavailableReason
                    status is UnraidVmService.Status.Disabled -> DISABLED_REASON
                    status is UnraidVmService.Status.Unavailable -> status.reason
                    else -> "Unraid answered for the first time while this query ran, ask again"
                },
                lastSuccessfulPoll = snapshot?.lastSuccessfulPoll
            ),
            workloads = snapshot?.vms.orEmpty().map { it.asWorkload() }
        )
    }

    private fun UnraidVm.asWorkload() = Workload(
        kind = WorkloadKind.VIRTUAL_MACHINE,
        platform = platform,
        id = id,
        name = name,
        runState = RunStates.unraid(state),
        source = this
    )
}
