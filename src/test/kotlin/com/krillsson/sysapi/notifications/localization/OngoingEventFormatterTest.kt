package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.core.check.CheckService
import com.krillsson.sysapi.core.check.HttpCheck
import com.krillsson.sysapi.core.check.HttpMethod
import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.storagepool.StoragePoolService
import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant
import java.util.UUID

class OngoingEventFormatterTest {

    private val checkService: CheckService = mockk()
    private val storagePoolService: StoragePoolService = mockk {
        every { displayName("md:md5") } returns "md5"
    }
    private val temperatureFormatter: TemperatureFormatter = mockk {
        every { format(any()) } answers { "${firstArg<Int>()}°C" }
    }
    private val formatter = OngoingEventFormatter(
        MonitoredValueFormatter(temperatureFormatter, ByteFormatter()),
        CheckEventFormatter(checkService),
        StoragePoolEventFormatter(storagePoolService)
    )

    private val checkId: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private fun ongoingEvent(monitoredItemId: String?) = Notification.OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = monitoredItemId,
        monitorType = Monitor.Type.WEBSERVER_UP,
        startTime = Instant.now(),
        threshold = MonitoredValue.ConditionalValue(true),
        warningThreshold = null,
        value = MonitoredValue.ConditionalValue(false),
        inertia = Duration.ZERO,
        severity = EventSeverity.CRITICAL
    )

    private fun thresholdEvent(
        monitorType: Monitor.Type,
        severity: EventSeverity,
        threshold: Long,
        warningThreshold: Long?,
        value: Long,
        monitoredItemId: String? = null
    ) = Notification.OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = monitoredItemId,
        monitorType = monitorType,
        startTime = Instant.now(),
        threshold = MonitoredValue.NumericalValue(threshold),
        warningThreshold = warningThreshold?.let { MonitoredValue.NumericalValue(it) },
        value = MonitoredValue.NumericalValue(value),
        inertia = Duration.ZERO,
        severity = severity
    )

    private fun storagePoolEvent(state: StoragePoolState) = Notification.OngoingEvent(
        id = UUID.randomUUID(),
        monitorId = UUID.randomUUID(),
        monitoredItemId = "md:md5",
        monitorType = Monitor.Type.STORAGE_POOL_HEALTH,
        startTime = Instant.now(),
        threshold = MonitoredValue.EnumValue(StoragePoolState.ONLINE),
        warningThreshold = null,
        value = MonitoredValue.EnumValue(state),
        inertia = Duration.ZERO,
        severity = EventSeverity.CRITICAL
    )

    @ParameterizedTest
    @CsvSource(
        "DEGRADED, Storage pool degraded on server, Pool md5 is degraded",
        "FAULTED, Storage pool faulted on server, Pool md5 is faulted",
        "UNAVAIL, Storage pool unavailable on server, Pool md5 is unavailable",
    )
    fun `names the state a storage pool is in`(state: StoragePoolState, expectedTitle: String, expectedDescription: String) {
        // Given
        val notification = storagePoolEvent(state)

        // When
        val title = formatter.formatOngoingEventTitle(notification, "server")
        val description = formatter.formatOngoingEventDescription(notification)

        // Then
        title shouldBe expectedTitle
        description shouldBe expectedDescription
    }

    @ParameterizedTest
    @CsvSource(
        "CPU_TEMP, WARNING, CPU temp. high on server",
        "CPU_TEMP, CRITICAL, CPU temp. critical on server",
        "FILE_SYSTEM_SPACE, WARNING, Space low on server",
        "FILE_SYSTEM_SPACE, CRITICAL, Space critical on server",
        "MEMORY_SPACE, WARNING, Memory space low on server",
        "CONTAINER_RUNNING, CRITICAL, Container stopped on server",
    )
    fun `names the severity in the title`(monitorType: Monitor.Type, severity: EventSeverity, expected: String) {
        // Given
        val notification = thresholdEvent(monitorType, severity, threshold = 90, warningThreshold = 60, value = 67)

        // When
        val title = formatter.formatOngoingEventTitle(notification, "server")

        // Then
        title shouldBe expected
    }

    @Test
    fun `describes a warning by the warning threshold and where critical is`() {
        // Given
        val notification = thresholdEvent(
            Monitor.Type.CPU_TEMP, EventSeverity.WARNING, threshold = 90, warningThreshold = 60, value = 67
        )

        // When
        val description = formatter.formatOngoingEventDescription(notification)

        // Then
        description shouldBe "Temperature went above 60°C to 67°C, critical at 90°C"
    }

    @Test
    fun `describes a warning on a monitor that alerts below its threshold`() {
        // Given
        val notification = thresholdEvent(
            Monitor.Type.FILE_SYSTEM_SPACE,
            EventSeverity.WARNING,
            threshold = 100,
            warningThreshold = 500,
            value = 300,
            monitoredItemId = "/mnt"
        )

        // When
        val description = formatter.formatOngoingEventDescription(notification)

        // Then
        description shouldBe "Space on /mnt went below 500 B to 300 B, critical at 100 B"
    }

    @ParameterizedTest
    @CsvSource(
        "60, 93",
        ", 93",
    )
    fun `describes a critical event by the critical threshold only`(warningThreshold: Long?, value: Long) {
        // Given
        val notification = thresholdEvent(
            Monitor.Type.CPU_TEMP, EventSeverity.CRITICAL, threshold = 90, warningThreshold = warningThreshold, value = value
        )

        // When
        val description = formatter.formatOngoingEventDescription(notification)

        // Then
        description shouldBe "Temperature went above 90°C to 93°C"
    }

    @Test
    fun `adds the critical threshold to a check latency warning`() {
        // Given
        val notification = thresholdEvent(
            Monitor.Type.CHECK_LATENCY,
            EventSeverity.WARNING,
            threshold = 1000,
            warningThreshold = 500,
            value = 600,
            monitoredItemId = "example.com"
        )

        // When
        val description = formatter.formatOngoingEventDescription(notification)

        // Then
        description shouldBe "example.com answered in 600ms, above 500ms, critical at 1000ms"
    }

    @Test
    fun `adds the critical threshold to a certificate expiry warning`() {
        // Given
        val notification = thresholdEvent(
            Monitor.Type.CHECK_CERTIFICATE_EXPIRY,
            EventSeverity.WARNING,
            threshold = 7,
            warningThreshold = 30,
            value = 20,
            monitoredItemId = "example.com"
        )

        // When
        val description = formatter.formatOngoingEventDescription(notification)

        // Then
        description shouldBe "The certificate of example.com expires in 20 days, below 30 days, critical at 7 days"
    }

    @Test
    fun `delegates the webserver up description to the check event formatter`() {
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
}
