package com.krillsson.sysapi.workload

import java.time.Instant

fun workload(
    id: String,
    name: String = "workload-$id",
    kind: WorkloadKind = WorkloadKind.CONTAINER,
    platform: WorkloadPlatform = WorkloadPlatform.DOCKER,
    runState: RunState = RunState.RUNNING
): Workload = Workload(kind = kind, platform = platform, id = id, name = name, runState = runState, source = "$platform:$id")

fun available(platform: WorkloadPlatform, lastSuccessfulPoll: Instant? = null) = PlatformAvailability(
    platform = platform,
    available = true,
    isDisabled = false,
    reason = null,
    lastSuccessfulPoll = lastSuccessfulPoll
)

class FakeWorkloadSource(
    override val platform: WorkloadPlatform,
    override val kinds: Set<WorkloadKind>,
    private val reading: () -> WorkloadSource.Reading
) : WorkloadSource {
    override fun read(): WorkloadSource.Reading = reading()
}
