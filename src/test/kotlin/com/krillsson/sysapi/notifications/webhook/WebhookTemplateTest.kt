package com.krillsson.sysapi.notifications.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.notifications.NotificationEventType
import com.krillsson.sysapi.notifications.NotificationParameters
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class WebhookTemplateTest {

    private val template = WebhookTemplate(ObjectMapper())

    private fun parameters(eventId: String?) = NotificationParameters(
        title = "title",
        message = "message",
        clickUrl = "https://monitee.app",
        priority = 3,
        eventType = NotificationEventType.ONGOING_EVENT,
        monitorType = null,
        severity = null,
        timestamp = Instant.parse("2026-09-08T12:00:00Z"),
        serverName = "server",
        serverId = "server-id",
        eventId = eventId
    )

    @Test
    fun `substitutes the event id`() {
        // When
        val body = template.renderBody(
            """{"id": "{{eventId}}"}""",
            parameters("11111111-2222-3333-4444-555555555555"),
            "application/json"
        )

        // Then
        body shouldBe """{"id": "11111111-2222-3333-4444-555555555555"}"""
    }

    @Test
    fun `leaves the event id empty for a notification without one`() {
        // When
        val body = template.renderBody("""{"id": "{{eventId}}"}""", parameters(null), "application/json")

        // Then
        body shouldBe """{"id": ""}"""
    }
}
