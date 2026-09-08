package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.persistence.KeyValueRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SnoozeNotificationsServiceTest {

    private val store = mutableMapOf<String, String>()
    private lateinit var repository: KeyValueRepository
    private var now = Instant.parse("2026-09-08T12:00:00Z")
    private lateinit var clock: Clock
    private lateinit var service: SnoozeNotificationsService

    @BeforeEach
    fun setUp() {
        store.clear()
        repository = inMemoryKeyValueRepository(store)
        clock = mockk()
        every { clock.instant() } answers { now }
        every { clock.zone } returns ZoneOffset.UTC
        service = SnoozeNotificationsService(repository, clock)
    }

    @Test
    fun `snoozes with a deadline`() {
        // When
        val info = service.snooze(now.plus(Duration.ofHours(1)), "Upgrading unRAID")

        // Then
        info.snoozed shouldBe true
        info.snoozedAt shouldBe now
        info.snoozedUntil shouldBe now.plus(Duration.ofHours(1))
        info.reason shouldBe "Upgrading unRAID"
    }

    @Test
    fun `expires lazily once the deadline passes`() {
        // Given
        service.snooze(now.plus(Duration.ofHours(1)), null)

        // When
        now = now.plus(Duration.ofHours(1)).minusSeconds(1)
        val stillSnoozed = service.isSnoozed()

        // Then
        stillSnoozed shouldBe true

        // When
        now = now.plusSeconds(1)
        val info = service.info()

        // Then
        info.snoozed shouldBe false
        info.snoozedAt shouldBe null
        info.snoozedUntil shouldBe null
    }

    @Test
    fun `an open-ended snooze survives a simulated restart`() {
        // Given
        service.snooze(null, "on call")

        // When
        val afterRestart = SnoozeNotificationsService(repository, clock)

        // Then
        val info = afterRestart.info()
        info.snoozed shouldBe true
        info.snoozedUntil shouldBe null
        info.reason shouldBe "on call"
    }

    @Test
    fun `resume clears an active snooze`() {
        // Given
        service.snooze(now.plus(Duration.ofHours(8)), "reason")

        // When
        val info = service.resume()

        // Then
        info.snoozed shouldBe false
        info.snoozedAt shouldBe null
        info.snoozedUntil shouldBe null
        info.reason shouldBe null
    }

    @Test
    fun `a second snooze replaces the deadline`() {
        // Given
        service.snooze(now.plus(Duration.ofHours(1)), null)

        // When
        val info = service.snooze(now.plus(Duration.ofHours(8)), null)

        // Then
        info.snoozedUntil shouldBe now.plus(Duration.ofHours(8))
    }

    @Test
    fun `suppressed count resets when a new snooze begins`() {
        // Given
        service.snooze(now.plus(Duration.ofHours(1)), null)
        service.recordSuppressed()
        service.recordSuppressed()

        // When
        val info = service.snooze(now.plus(Duration.ofHours(1)), null)

        // Then
        info.suppressedNotificationCount shouldBe 0
    }
}

private fun inMemoryKeyValueRepository(store: MutableMap<String, String>): KeyValueRepository {
    val repository = mockk<KeyValueRepository>()
    every { repository.get(any()) } answers { store[firstArg()] }
    every { repository.put(any(), anyNullable()) } answers {
        val key = firstArg<String>()
        val value = secondArg<String?>()
        if (value != null) store[key] = value else store.remove(key)
    }
    every { repository.remove(any()) } answers { store.remove(firstArg<String>()) != null }
    return repository
}
