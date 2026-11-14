package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandInput
import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandOutputFailed
import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandOutputStarted
import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommandJob
import com.krillsson.sysapi.workload.PowerCommandJobs
import com.krillsson.sysapi.workload.WorkloadPlatform
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID

class PowerCommandResolverTest {

    private val jobs = mockk<PowerCommandJobs>()
    private val resolver = PowerCommandResolver(jobs)
    private val runningJob = mockk<PowerCommandJob>()
    private val jobId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `answers with the running job when no job id is given`() {
        // Given
        every { jobs.current() } returns runningJob

        // When
        val job = resolver.powerCommandJob(null)

        // Then
        job shouldBe runningJob
    }

    @Test
    fun `answers with nothing for a job id it does not know, rather than the running job`() {
        // Given
        every { jobs.find(jobId) } returns null
        every { jobs.current() } returns runningJob

        // When
        val job = resolver.powerCommandJob(jobId)

        // Then
        job.shouldBeNull()
    }

    @Test
    fun `answers a started command with its job and a refused one with the reason`() {
        // Given
        every { jobs.start(WorkloadPlatform.UNRAID, "vm-1", PowerCommand.START) } returns
            PowerCommandJobs.StartResult.Started(jobId, WorkloadPlatform.UNRAID, "vm-1")
        every { jobs.start(WorkloadPlatform.UNRAID, "vm-2", PowerCommand.START) } returns
            PowerCommandJobs.StartResult.Rejected("Windows 11 is running and does not accept START")

        // When
        val started = resolver.performPowerCommand(PerformPowerCommandInput(WorkloadPlatform.UNRAID, "vm-1", PowerCommand.START))
        val refused = resolver.performPowerCommand(PerformPowerCommandInput(WorkloadPlatform.UNRAID, "vm-2", PowerCommand.START))

        // Then
        started shouldBe PerformPowerCommandOutputStarted(jobId, WorkloadPlatform.UNRAID, "vm-1")
        refused shouldBe PerformPowerCommandOutputFailed("Windows 11 is running and does not accept START")
    }
}
