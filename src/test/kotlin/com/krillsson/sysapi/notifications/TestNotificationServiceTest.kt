package com.krillsson.sysapi.notifications

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class TestNotificationServiceTest {

    private var now = Instant.parse("2026-11-07T12:00:00Z")
    private lateinit var clock: Clock
    private lateinit var notificationManager: NotificationManager
    private lateinit var service: TestNotificationService

    @BeforeEach
    fun setUp() {
        clock = mockk()
        every { clock.instant() } answers { now }
        every { clock.zone } returns ZoneOffset.UTC
        notificationManager = mockk {
            every { sendTest() } returns listOf("ntfy", "Home Assistant")
        }
        service = TestNotificationService(notificationManager, clock)
    }

    private fun sendUpToTheLimit() {
        repeat(TestNotificationService.LIMIT_PER_WINDOW) {
            service.send()
            now = now.plus(Duration.ofMinutes(1))
        }
    }

    @Test
    fun `sends to every enabled channel and counts down what is left`() {
        // When
        val result = service.send()

        // Then
        result shouldBe TestNotificationResult(
            sent = true,
            channels = listOf("ntfy", "Home Assistant"),
            remaining = TestNotificationService.LIMIT_PER_WINDOW - 1,
            retryAfter = null
        )
    }

    @Test
    fun `refuses once the hourly limit is used up and says when the oldest frees up`() {
        // Given
        val first = now
        sendUpToTheLimit()

        // When
        val result = service.send()

        // Then
        result shouldBe TestNotificationResult(
            sent = false,
            channels = emptyList(),
            remaining = 0,
            retryAfter = first.plus(Duration.ofHours(1))
        )
        verify(exactly = TestNotificationService.LIMIT_PER_WINDOW) { notificationManager.sendTest() }
    }

    @Test
    fun `reports retryAfter on the send that uses up the limit`() {
        // Given
        val first = now
        repeat(TestNotificationService.LIMIT_PER_WINDOW - 1) { service.send() }

        // When
        val result = service.send()

        // Then
        result.sent shouldBe true
        result.remaining shouldBe 0
        result.retryAfter shouldBe first.plus(Duration.ofHours(1))
    }

    @Test
    fun `allows another send once the oldest falls out of the hour`() {
        // Given
        val first = now
        sendUpToTheLimit()
        now = first.plus(Duration.ofHours(1))

        // When
        val result = service.send()

        // Then
        result.sent shouldBe true
        result.remaining shouldBe 0
    }

    @Test
    fun `does not count a send when no channel is enabled`() {
        // Given
        every { notificationManager.sendTest() } returns emptyList()

        // When
        val result = service.send()

        // Then
        result.sent shouldBe false
        result.remaining shouldBe TestNotificationService.LIMIT_PER_WINDOW
    }
}
