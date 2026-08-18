package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.check.CheckService
import com.krillsson.sysapi.core.check.HttpCheck
import com.krillsson.sysapi.core.check.HttpMethod
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID

class CheckEventFormatterTest {

    private val checkService: CheckService = mockk()
    private val formatter = CheckEventFormatter(checkService)

    private val checkId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private fun httpCheck(name: String) = HttpCheck(
        id = checkId,
        name = name,
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

    @Test
    fun `uses the check's name when it can still be resolved`() {
        // Given
        every { checkService.getById(checkId) } returns httpCheck("example.com")

        // When
        val ongoing = formatter.formatWebServerUpOngoingDescription(checkId.toString())
        val resolved = formatter.formatWebServerUpResolvedDescription(checkId.toString(), "5 minutes")

        // Then
        ongoing shouldBe "example.com is failing its check"
        resolved shouldBe "example.com is passing its check again after 5 minutes"
    }

    @Test
    fun `falls back to the raw monitored item id when the check can no longer be resolved`() {
        // Given
        every { checkService.getById(checkId) } returns null

        // When
        val ongoing = formatter.formatWebServerUpOngoingDescription(checkId.toString())
        val resolved = formatter.formatWebServerUpResolvedDescription(checkId.toString(), "5 minutes")

        // Then
        ongoing shouldBe "$checkId is failing its check"
        resolved shouldBe "$checkId is passing its check again after 5 minutes"
    }

    @Test
    fun `falls back to the raw monitored item id when it is not a valid check id`() {
        // When
        val ongoing = formatter.formatWebServerUpOngoingDescription("not-a-uuid")

        // Then
        ongoing shouldBe "not-a-uuid is failing its check"
    }

    @Test
    fun `formats check latency descriptions with the resolved name`() {
        // Given
        every { checkService.getById(checkId) } returns httpCheck("example.com")

        // When
        val ongoing = formatter.formatCheckLatencyOngoingDescription(checkId.toString(), "500ms", "300ms")
        val resolved = formatter.formatCheckLatencyResolvedDescription(checkId.toString(), "300ms", "150ms", "5 minutes")

        // Then
        ongoing shouldBe "example.com answered in 500ms, above 300ms"
        resolved shouldBe "example.com is back below 300ms at 150ms after 5 minutes"
    }
}
