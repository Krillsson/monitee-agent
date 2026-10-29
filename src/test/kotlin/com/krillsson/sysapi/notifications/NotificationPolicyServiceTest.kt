package com.krillsson.sysapi.notifications

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.krillsson.sysapi.notifications.NotificationPolicyStore.StoredNotificationPolicy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class NotificationPolicyServiceTest {

    private val written = mutableListOf<StoredNotificationPolicy>()
    private var now = Instant.parse("2026-09-08T12:00:00Z")
    private lateinit var clock: Clock
    private lateinit var service: NotificationPolicyService

    @BeforeEach
    fun setUp() {
        written.clear()
        clock = mockk()
        every { clock.instant() } answers { now }
        every { clock.zone } returns ZoneOffset.UTC
        service = NotificationPolicyService(inMemoryNotificationPolicyStore(written), clock)
    }

    @Test
    fun `starts with quiet hours off, a five minute cooldown and recovery notifications on`() {
        // When
        val policy = service.policy()

        // Then
        policy shouldBe NotificationPolicy(
            quietHours = QuietHours(
                enabled = false,
                start = "22:00",
                end = "07:00",
                days = DayOfWeek.entries.toList(),
                timeZone = ZoneId.systemDefault().id,
                allowCritical = false,
                active = false
            ),
            cooldownMinutes = 5,
            notifyOnRecovery = true,
            suppressed = SuppressedNotificationCounts(acknowledged = 0, cooldown = 0, quietHours = 0, recovery = 0)
        )
    }

    @Test
    fun `stores nothing until something is changed`() {
        // When
        service.policy()

        // Then
        written.shouldBeEmpty()
    }

    @Test
    fun `leaves the fields an update omits alone`() {
        // Given
        service.update(
            NotificationPolicyUpdate(
                quietHours = QuietHoursUpdate(enabled = true, start = "23:30", timeZone = "Europe/Stockholm"),
                cooldownMinutes = 15
            )
        )

        // When
        val policy = service.update(NotificationPolicyUpdate(notifyOnRecovery = false))

        // Then
        policy.quietHours.enabled shouldBe true
        policy.quietHours.start shouldBe "23:30"
        policy.quietHours.end shouldBe "07:00"
        policy.quietHours.timeZone shouldBe "Europe/Stockholm"
        policy.cooldownMinutes shouldBe 15
        policy.notifyOnRecovery shouldBe false
    }

    @Test
    fun `reads back what was stored after a restart`() {
        // Given
        service.update(
            NotificationPolicyUpdate(
                quietHours = QuietHoursUpdate(
                    enabled = true,
                    start = "21:00",
                    end = "06:30",
                    days = listOf(DayOfWeek.SATURDAY, DayOfWeek.FRIDAY),
                    timeZone = "America/New_York",
                    allowCritical = true
                ),
                cooldownMinutes = 0,
                notifyOnRecovery = false
            )
        )

        // When
        val restarted = NotificationPolicyService(inMemoryNotificationPolicyStore(written), clock).policy()

        // Then
        restarted.quietHours shouldBe QuietHours(
            enabled = true,
            start = "21:00",
            end = "06:30",
            days = listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
            timeZone = "America/New_York",
            allowCritical = true,
            active = false
        )
        restarted.cooldownMinutes shouldBe 0
        restarted.notifyOnRecovery shouldBe false
    }

    @Test
    fun `fills in defaults for what a stored file leaves out`() {
        // Given
        written += jacksonObjectMapper().readValue<StoredNotificationPolicy>(
            """{ "quietHours": { "enabled": true, "timeZone": "UTC" }, "cooldownMinutes": 10 }"""
        )

        // When
        val policy = NotificationPolicyService(inMemoryNotificationPolicyStore(written), clock).policy()

        // Then
        policy.quietHours.enabled shouldBe true
        policy.quietHours.start shouldBe "22:00"
        policy.quietHours.end shouldBe "07:00"
        policy.quietHours.days shouldBe DayOfWeek.entries.toList()
        policy.cooldownMinutes shouldBe 10
        policy.notifyOnRecovery shouldBe true
    }

    @Test
    fun `reports quiet hours as active inside the window`() {
        // Given
        now = Instant.parse("2026-09-08T23:00:00Z")

        // When
        val policy = service.update(NotificationPolicyUpdate(quietHours = QuietHoursUpdate(enabled = true, timeZone = "UTC")))

        // Then
        policy.quietHours.active shouldBe true
        service.isQuietHoursActive() shouldBe true
    }

    @ParameterizedTest
    @ValueSource(strings = ["7:00", "24:00", "07:60", "0700", "seven"])
    fun `rejects a time that is not HH mm`(time: String) {
        // When
        val failure = shouldThrow<InvalidNotificationPolicyException> {
            service.update(NotificationPolicyUpdate(quietHours = QuietHoursUpdate(start = time)))
        }

        // Then
        failure.message shouldContain "HH:mm"
        written.shouldBeEmpty()
    }

    @Test
    fun `rejects an unknown time zone`() {
        // When
        val failure = shouldThrow<InvalidNotificationPolicyException> {
            service.update(NotificationPolicyUpdate(quietHours = QuietHoursUpdate(timeZone = "Europe/Atlantis")))
        }

        // Then
        failure.message shouldContain "Europe/Atlantis"
    }

    @Test
    fun `rejects a window that starts and ends at the same time`() {
        // When
        shouldThrow<InvalidNotificationPolicyException> {
            service.update(NotificationPolicyUpdate(quietHours = QuietHoursUpdate(start = "07:00")))
        }
    }

    @Test
    fun `rejects quiet hours without days`() {
        // When
        shouldThrow<InvalidNotificationPolicyException> {
            service.update(NotificationPolicyUpdate(quietHours = QuietHoursUpdate(days = emptyList())))
        }
    }

    @Test
    fun `rejects a negative cooldown without storing the rest of the update`() {
        // When
        shouldThrow<InvalidNotificationPolicyException> {
            service.update(NotificationPolicyUpdate(quietHours = QuietHoursUpdate(enabled = true), cooldownMinutes = -1))
        }

        // Then
        written.shouldBeEmpty()
    }

    @Test
    fun `counts suppressed notifications per reason`() {
        // When
        service.recordSuppressed(SuppressionReason.COOLDOWN)
        service.recordSuppressed(SuppressionReason.COOLDOWN)
        service.recordSuppressed(SuppressionReason.RECOVERY)

        // Then
        service.policy().suppressed shouldBe SuppressedNotificationCounts(
            acknowledged = 0,
            cooldown = 2,
            quietHours = 0,
            recovery = 1
        )
    }
}
