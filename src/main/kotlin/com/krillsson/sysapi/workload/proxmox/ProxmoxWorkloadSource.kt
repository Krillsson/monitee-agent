package com.krillsson.sysapi.workload.proxmox

import com.krillsson.sysapi.graphql.domain.ProxmoxInfoAvailable
import com.krillsson.sysapi.graphql.domain.ProxmoxInfoUnavailable
import com.krillsson.sysapi.graphql.domain.info
import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestKind
import com.krillsson.sysapi.proxmox.ProxmoxService
import com.krillsson.sysapi.workload.PlatformAvailability
import com.krillsson.sysapi.workload.RunStates
import com.krillsson.sysapi.workload.Workload
import com.krillsson.sysapi.workload.WorkloadKind
import com.krillsson.sysapi.workload.WorkloadPlatform
import com.krillsson.sysapi.workload.WorkloadSource
import org.springframework.stereotype.Component

@Component
class ProxmoxWorkloadSource(private val proxmoxService: ProxmoxService) : WorkloadSource {

    override val platform = WorkloadPlatform.PROXMOX
    override val kinds = setOf(WorkloadKind.VIRTUAL_MACHINE, WorkloadKind.CONTAINER)

    override fun read(): WorkloadSource.Reading = when (val info = proxmoxService.info()) {
        is ProxmoxInfoAvailable -> WorkloadSource.Reading(
            availability = PlatformAvailability(
                platform = platform,
                available = true,
                isDisabled = false,
                reason = null,
                lastSuccessfulPoll = info.snapshot.lastSuccessfulPoll
            ),
            workloads = info.snapshot.guests.map { it.asWorkload() }
        )

        is ProxmoxInfoUnavailable -> WorkloadSource.Reading(
            availability = PlatformAvailability(
                platform = platform,
                available = false,
                isDisabled = info.isDisabled,
                reason = info.reason,
                lastSuccessfulPoll = null
            ),
            workloads = emptyList()
        )
    }

    private fun ProxmoxGuest.asWorkload() = Workload(
        kind = workloadKind(kind),
        platform = platform,
        id = id,
        name = name,
        runState = RunStates.proxmox(status),
        source = this
    )

    companion object {
        fun workloadKind(kind: ProxmoxGuestKind): WorkloadKind = when (kind) {
            ProxmoxGuestKind.QEMU -> WorkloadKind.VIRTUAL_MACHINE
            ProxmoxGuestKind.LXC -> WorkloadKind.CONTAINER
        }
    }
}
