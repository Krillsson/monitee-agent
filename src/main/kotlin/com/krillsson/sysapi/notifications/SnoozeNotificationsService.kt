package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.persistence.KeyValueRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@Service
class SnoozeNotificationsService(
    private val keyValueRepository: KeyValueRepository,
    private val clock: Clock
) {
    private val suppressedCount = AtomicInteger(0)

    fun isSnoozed(): Boolean {
        if (readInstant(SNOOZED_AT_KEY) == null) return false
        val snoozedUntil = readInstant(SNOOZED_UNTIL_KEY)
        if (snoozedUntil != null && !snoozedUntil.isAfter(clock.instant())) {
            clear()
            return false
        }
        return true
    }

    fun recordSuppressed() {
        suppressedCount.incrementAndGet()
    }

    fun snooze(until: Instant?, reason: String?): SnoozeNotificationsInfo {
        keyValueRepository.put(SNOOZED_AT_KEY, clock.instant().toString())
        keyValueRepository.put(SNOOZED_UNTIL_KEY, until?.toString())
        keyValueRepository.put(REASON_KEY, reason)
        suppressedCount.set(0)
        return info()
    }

    fun resume(): SnoozeNotificationsInfo {
        clear()
        return info()
    }

    fun info(): SnoozeNotificationsInfo {
        if (!isSnoozed()) {
            return SnoozeNotificationsInfo(
                snoozed = false,
                snoozedAt = null,
                snoozedUntil = null,
                reason = null,
                suppressedNotificationCount = suppressedCount.get()
            )
        }
        return SnoozeNotificationsInfo(
            snoozed = true,
            snoozedAt = readInstant(SNOOZED_AT_KEY),
            snoozedUntil = readInstant(SNOOZED_UNTIL_KEY),
            reason = keyValueRepository.get(REASON_KEY),
            suppressedNotificationCount = suppressedCount.get()
        )
    }

    private fun clear() {
        keyValueRepository.remove(SNOOZED_AT_KEY)
        keyValueRepository.remove(SNOOZED_UNTIL_KEY)
        keyValueRepository.remove(REASON_KEY)
    }

    private fun readInstant(key: String): Instant? = keyValueRepository.get(key)?.let { Instant.parse(it) }

    companion object {
        private const val SNOOZED_AT_KEY = "snooze.snoozedAt"
        private const val SNOOZED_UNTIL_KEY = "snooze.snoozedUntil"
        private const val REASON_KEY = "snooze.reason"
    }
}

data class SnoozeNotificationsInfo(
    val snoozed: Boolean,
    val snoozedAt: Instant?,
    val snoozedUntil: Instant?,
    val reason: String?,
    val suppressedNotificationCount: Int
)
