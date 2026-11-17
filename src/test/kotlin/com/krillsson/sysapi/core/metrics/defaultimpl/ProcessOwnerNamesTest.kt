package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.workload.TestClock
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import oshi.software.os.OSProcess
import java.time.Duration

class ProcessOwnerNamesTest {

    private val clock = TestClock()
    private val ownerNames = ProcessOwnerNames(Duration.ofMinutes(5), clock.clock)
    private val lookups = mutableListOf<String>()

    private fun process(uid: String, gid: String, users: List<String>, groups: List<String>): OSProcess {
        val remainingUsers = ArrayDeque(users)
        val remainingGroups = ArrayDeque(groups)
        return mockk {
            every { userID } returns uid
            every { groupID } returns gid
            every { user } answers { lookups += "user $uid"; remainingUsers.removeFirst() }
            every { group } answers { lookups += "group $gid"; remainingGroups.removeFirst() }
        }
    }

    @Test
    fun `looks up each user and group once for processes that share them`() {
        // Given
        val processes = List(3) { process("0", "0", listOf("root"), listOf("root")) } +
            process("99", "100", listOf("nobody"), listOf("users"))

        // When
        ownerNames.refresh(processes)
        val owners = processes.map { ownerNames.userOf(it) to ownerNames.groupOf(it) }

        // Then
        owners shouldBe List(3) { "root" to "root" } + ("nobody" to "users")
        lookups.sorted() shouldBe listOf("group 0", "group 100", "user 0", "user 99")
    }

    @Test
    fun `keeps answering with the cached names while they are younger than the max age`() {
        // Given
        val process = process("1000", "1000", listOf("krillsson", "renamed"), listOf("staff", "renamed"))
        ownerNames.refresh(listOf(process))

        // When
        clock.advance(Duration.ofMinutes(4))
        ownerNames.refresh(listOf(process))

        // Then
        (ownerNames.userOf(process) to ownerNames.groupOf(process)) shouldBe ("krillsson" to "staff")
    }

    @Test
    fun `looks the names up again on the refresh after the max age`() {
        // Given
        val process = process("1000", "1000", listOf("krillsson", "renamed"), listOf("staff", "renamed"))
        ownerNames.refresh(listOf(process))

        // When
        clock.advance(Duration.ofMinutes(5))
        ownerNames.refresh(listOf(process))

        // Then
        (ownerNames.userOf(process) to ownerNames.groupOf(process)) shouldBe ("renamed" to "renamed")
    }

    @Test
    fun `does not look stale names up again when asked between refreshes`() {
        // Given
        val process = process("1000", "1000", listOf("krillsson", "renamed"), listOf("staff", "renamed"))
        ownerNames.refresh(listOf(process))

        // When
        clock.advance(Duration.ofMinutes(10))
        val owner = ownerNames.userOf(process) to ownerNames.groupOf(process)

        // Then
        owner shouldBe ("krillsson" to "staff")
    }

    @Test
    fun `looks up the owner of a process the last refresh did not see`() {
        // Given
        ownerNames.refresh(listOf(process("0", "0", listOf("root"), listOf("root"))))
        val newProcess = process("1000", "1000", listOf("krillsson"), listOf("staff"))

        // When
        val owner = ownerNames.userOf(newProcess) to ownerNames.groupOf(newProcess)

        // Then
        owner shouldBe ("krillsson" to "staff")
    }

    @Test
    fun `forgets users and groups no process has any more`() {
        // Given
        val gone = process("1000", "1000", listOf("krillsson", "reused"), listOf("staff", "reused"))
        ownerNames.refresh(listOf(gone))

        // When
        ownerNames.refresh(listOf(process("0", "0", listOf("root"), listOf("root"))))
        val owner = ownerNames.userOf(gone) to ownerNames.groupOf(gone)

        // Then
        owner shouldBe ("reused" to "reused")
    }
}
