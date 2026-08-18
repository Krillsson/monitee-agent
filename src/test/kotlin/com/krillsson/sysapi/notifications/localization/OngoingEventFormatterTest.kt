package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.check.CheckService
import com.krillsson.sysapi.core.check.CheckType
import com.krillsson.sysapi.core.check.HttpCheck
import com.krillsson.sysapi.core.check.HttpMethod
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.notifications.Notification
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OngoingEventFormatterTest {

    private val checkService: CheckService = mockk()
    private val formatter = OngoingEventFormatter(MonitoredValueFormatter(mockk(), mockk()), checkService)

    private val checkId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private fun ongoingEvent(monitoredItemId: String?) = Notification.OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = monitoredItemId,
        monitorType = Monitor.Type.WEBSERVER_UP,
        startTime = Instant.now(),
        threshold = MonitoredValue.ConditionalValue(true),
        value = MonitoredValue.ConditionalValue(false),
        inertia = Duration.ZERO
    )

    @Test
    fun `uses the check's name in the description when it can still be resolved`() {
        // Given
        every { checkService.getById(checkId) } returns HttpCheck(
            id = checkId,
            name = "example.com",
            enabled = true,
            intervalSeconds = 60,
            timeoutSeconds = 20,
            url = "https://example.com",
            method = HttpMethod.GET,
            expectedStatusCodes = "200-299",
            keyword = null,
            keywordInverted = false,
            ignoreCertificateErrors = false,
            followRedirects = true,
            headers = emptyList()
        )

        // When
        val description = formatter.formatOngoingEventDescription(ongoingEvent(checkId.toString()))

        // Then
        description shouldBe "example.com is failing its check"
    }

    @Test
    fun `falls back to the raw monitored item id when the check can no longer be resolved`() {
        // Given
        every { checkService.getById(checkId) } returns null

        // When
        val description = formatter.formatOngoingEventDescription(ongoingEvent(checkId.toString()))

        // Then
        description shouldBe "$checkId is failing its check"
    }
}
