package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJob
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommandJobs
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID

class ProxmoxResolverTest {

    private val jobs = mockk<ProxmoxGuestCommandJobs>()
    private val resolver = ProxmoxResolver(mockk(), jobs)
    private val runningJob = mockk<ProxmoxGuestCommandJob>()

    @Test
    fun `answers with the running job when no job id is given`() {
        // Given
        every { jobs.current() } returns runningJob

        // When
        val job = resolver.guestCommandJob(null)

        // Then
        job shouldBe runningJob
    }

    @Test
    fun `answers with nothing for a job id it does not know, rather than the running job`() {
        // Given
        val unknown = UUID.fromString("00000000-0000-0000-0000-000000000001")
        every { jobs.find(unknown) } returns null
        every { jobs.current() } returns runningJob

        // When
        val job = resolver.guestCommandJob(unknown)

        // Then
        job.shouldBeNull()
    }
}
