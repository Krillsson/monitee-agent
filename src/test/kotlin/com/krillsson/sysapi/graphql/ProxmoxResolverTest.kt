package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.graphql.domain.ProxmoxGuestCommandJob
import com.krillsson.sysapi.graphql.domain.ProxmoxInfoAvailable
import com.krillsson.sysapi.proxmox.ProxmoxCluster
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommandJobs
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxSnapshot
import com.krillsson.sysapi.proxmox.guest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ProxmoxResolverTest {

    private val jobs = mockk<ProxmoxGuestCommandJobs>()
    private val resolver = ProxmoxResolver(jobs)
    private val runningJob = mockk<ProxmoxGuestCommandJob>()

    private val info = ProxmoxInfoAvailable(
        ProxmoxSnapshot(
            cluster = ProxmoxCluster("homelab", quorate = true, nodeCount = 1),
            nodes = emptyList(),
            guests = listOf(guest(100), guest(101, status = ProxmoxGuestStatus.STOPPED)),
            storages = emptyList(),
            warnings = emptyList(),
            pveVersion = "9.0.3",
            lastSuccessfulPoll = Instant.parse("2026-11-11T10:00:00Z")
        )
    )

    @Test
    fun `answers every field from the snapshot the query started with`() {
        // When
        val cluster = resolver.cluster(info)
        val running = resolver.runningGuests(info)
        val stopped = resolver.guest(info, "101")

        // Then
        cluster.name shouldBe "homelab"
        running.map { it.id } shouldContainExactly listOf("100")
        stopped?.status shouldBe ProxmoxGuestStatus.STOPPED
        resolver.pveVersion(info) shouldBe "9.0.3"
        resolver.lastSuccessfulPoll(info) shouldBe Instant.parse("2026-11-11T10:00:00Z")
    }

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
