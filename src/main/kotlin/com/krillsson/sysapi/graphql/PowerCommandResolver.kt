package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandInput
import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandOutput
import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandOutputFailed
import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandOutputStarted
import com.krillsson.sysapi.workload.PowerCommandEvent
import com.krillsson.sysapi.workload.PowerCommandJob
import com.krillsson.sysapi.workload.PowerCommandJobs
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import java.util.UUID

@Controller
class PowerCommandResolver(
    private val powerCommandJobs: PowerCommandJobs
) {

    @MutationMapping
    fun performPowerCommand(@Argument input: PerformPowerCommandInput): PerformPowerCommandOutput =
        when (val result = powerCommandJobs.start(input.platform, input.id, input.command)) {
            is PowerCommandJobs.StartResult.Started -> PerformPowerCommandOutputStarted(result.jobId, result.platform, result.id)
            is PowerCommandJobs.StartResult.Rejected -> PerformPowerCommandOutputFailed(result.reason)
        }

    @SubscriptionMapping
    fun powerCommand(@Argument jobId: UUID): Flux<PowerCommandEvent> = powerCommandJobs.events(jobId)

    @QueryMapping
    fun powerCommandJob(@Argument jobId: UUID?): PowerCommandJob? = when (jobId) {
        null -> powerCommandJobs.current()
        else -> powerCommandJobs.find(jobId)
    }
}
