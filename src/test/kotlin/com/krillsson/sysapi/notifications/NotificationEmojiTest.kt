package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.Monitor
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.time.Instant
import org.junit.jupiter.api.Test

class NotificationEmojiTest {

    private fun parameters(severity: EventSeverity?) = NotificationParameters(
        title = "CPU load too high on nas",
        message = "Load went above 90% to 95%",
        clickUrl = "https://monitee.app",
        priority = 4,
        eventType = NotificationEventType.ONGOING_EVENT,
        monitorType = Monitor.Type.CPU_LOAD,
        severity = severity,
        timestamp = Instant.parse("2026-08-01T09:00:00Z"),
        serverName = "nas",
        serverId = "server"
    )

    @Test
    fun `marks a warning event apart from a critical one`() {
        // When
        val warning = NotificationEmoji.decorate(parameters(EventSeverity.WARNING))
        val critical = NotificationEmoji.decorate(parameters(EventSeverity.CRITICAL))

        // Then
        warning.title shouldStartWith "⚠️"
        critical.title shouldStartWith "🚨"
    }

    @Test
    fun `tags a warning event for ntfy`() {
        // When
        val warning = NotificationEmoji.ntfyTags(parameters(EventSeverity.WARNING))
        val critical = NotificationEmoji.ntfyTags(parameters(EventSeverity.CRITICAL))

        // Then
        warning shouldContain "warning"
        critical shouldContain "rotating_light"
    }
}
