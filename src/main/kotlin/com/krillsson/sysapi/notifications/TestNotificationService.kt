package com.krillsson.sysapi.notifications

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class TestNotificationService(
    private val notificationManager: NotificationManager,
    private val clock: Clock
) {
    private val sentAt = ArrayDeque<Instant>()

    @Synchronized
    fun send(): TestNotificationResult {
        val now = clock.instant()
        while (sentAt.firstOrNull()?.let { !it.plus(WINDOW).isAfter(now) } == true) {
            sentAt.removeFirst()
        }
        if (sentAt.size >= LIMIT_PER_WINDOW) {
            return result(sent = false, channels = emptyList())
        }
        val channels = notificationManager.sendTest()
        if (channels.isNotEmpty()) {
            sentAt.addLast(now)
        }
        return result(sent = channels.isNotEmpty(), channels = channels)
    }

    private fun result(sent: Boolean, channels: List<String>): TestNotificationResult {
        val remaining = LIMIT_PER_WINDOW - sentAt.size
        return TestNotificationResult(
            sent = sent,
            channels = channels,
            remaining = remaining,
            retryAfter = if (remaining == 0) sentAt.first().plus(WINDOW) else null
        )
    }

    companion object {
        const val LIMIT_PER_WINDOW = 20
        val WINDOW: Duration = Duration.ofHours(1)
    }
}

data class TestNotificationResult(
    val sent: Boolean,
    val channels: List<String>,
    val remaining: Int,
    val retryAfter: Instant?
)
