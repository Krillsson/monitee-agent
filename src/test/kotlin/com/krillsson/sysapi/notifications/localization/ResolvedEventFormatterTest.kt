package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.check.CheckService
import com.krillsson.sysapi.core.check.TcpCheck
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.notifications.Notification
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ResolvedEventFormatterTest {

    private val checkService: CheckService = mockk()
    private val formatter = ResolvedEventFormatter(
        MonitoredValueFormatter(mockk(), mockk()),
        DurationFormatter(),
        CheckEventFormatter(checkService)
    )

    private val checkId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private fun resolvedEvent(monitoredItemId: String?) = Notification.ResolvedEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = monitoredItemId,
        monitorType = Monitor.Type.WEBSERVER_UP,
        startTime = Instant.parse("2026-08-18T10:00:00Z"),
        endTime = Instant.parse("2026-08-18T10:05:00Z"),
        threshold = MonitoredValue.ConditionalValue(true),
        startValue = MonitoredValue.ConditionalValue(false),
        endValue = MonitoredValue.ConditionalValue(true)
    )

    @Test
    fun `delegates the webserver up description to the check event formatter`() {
        // Given
        every { checkService.getById(checkId) } returns TcpCheck(
            id = checkId,
            name = "nas.lan",
            enabled = true,
            intervalSeconds = 60,
            timeoutSeconds = 20,
            host = "nas.lan",
            port = 445
        )

        // When
        val description = formatter.formatResolvedEventDescription(resolvedEvent(checkId.toString()))

        // Then
        description shouldBe "nas.lan is passing its check again after 5 minutes"
    }
}
