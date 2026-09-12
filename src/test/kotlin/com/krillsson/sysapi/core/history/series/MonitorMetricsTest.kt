package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class MonitorMetricsTest {

    private val unsampled = setOf(
        Monitor.Type.DISK_SMART_HEALTH,
        Monitor.Type.WEBSERVER_UP,
        Monitor.Type.CHECK_LATENCY,
        Monitor.Type.CONTAINER_UPDATE_AVAILABLE,
        Monitor.Type.PROCESS_MEMORY_SPACE,
        Monitor.Type.PROCESS_CPU_LOAD,
        Monitor.Type.PROCESS_EXISTS
    )

    @ParameterizedTest
    @EnumSource(Monitor.Type::class)
    fun `maps every monitor type to a series or to a documented exclusion`(type: Monitor.Type) {
        // When
        val series = MonitorMetrics.seriesFor(type, monitoredItemId = "item-1")

        // Then
        if (type in unsampled) {
            series shouldBe null
        } else {
            series.shouldNotBeNull()
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = Monitor.Type::class,
        names = ["CPU_LOAD", "CPU_TEMP", "MEMORY_USED", "MEMORY_SPACE", "CONNECTIVITY", "EXTERNAL_IP_CHANGED",
            "LOAD_AVERAGE_ONE_MINUTE", "LOAD_AVERAGE_FIVE_MINUTES", "LOAD_AVERAGE_FIFTEEN_MINUTES"]
    )
    fun `uses the host wide item id for a host wide monitor`(type: Monitor.Type) {
        // When
        val series = MonitorMetrics.seriesFor(type, monitoredItemId = null)

        // Then
        series.shouldNotBeNull().itemId shouldBe MetricId.HOST_WIDE
    }

    @ParameterizedTest
    @EnumSource(
        value = Monitor.Type::class,
        names = ["FILE_SYSTEM_SPACE", "DISK_READ_RATE", "NETWORK_UP", "GPU_TEMPERATURE", "CONTAINER_RUNNING",
            "UPS_LOAD_PERCENTAGE"]
    )
    fun `requires a monitored item id for a per item monitor`(type: Monitor.Type) {
        // When
        val series = MonitorMetrics.seriesFor(type, monitoredItemId = null)

        // Then
        series shouldBe null
    }

    @Test
    fun `keeps the upload and download rates on separate series`() {
        // When
        val upload = MonitorMetrics.seriesFor(Monitor.Type.NETWORK_UPLOAD_RATE, "eth0")
        val download = MonitorMetrics.seriesFor(Monitor.Type.NETWORK_DOWNLOAD_RATE, "eth0")

        // Then
        upload.shouldNotBeNull().metric shouldBe MetricId.NETWORK_TX_BYTES_PER_SECOND
        download.shouldNotBeNull().metric shouldBe MetricId.NETWORK_RX_BYTES_PER_SECOND
    }

    @Test
    fun `collapses a gauge to its average`() {
        // Given
        val point = point(min = 1.0, avg = 5.0, max = 9.0, last = 2.0)

        // When
        val value = MonitorMetrics.collapse(point, MetricId.MEMORY_USED_BYTES)

        // Then
        value shouldBe MonitoredValue.NumericalValue(5)
    }

    @Test
    fun `collapses a fractional gauge to a fractional value`() {
        // Given
        val point = point(min = 1.0, avg = 42.5, max = 90.0, last = 2.0)

        // When
        val value = MonitorMetrics.collapse(point, MetricId.CPU_USAGE_PERCENT)

        // Then
        value shouldBe MonitoredValue.FractionalValue(42.5f)
    }

    @Test
    fun `collapses a counter to its last value rather than its average`() {
        // Given
        val point = point(min = 1.0, avg = 5.0, max = 9.0, last = 900.0)

        // When
        val value = MonitorMetrics.collapse(point, MetricId.NETWORK_RX_BYTES)

        // Then
        value shouldBe MonitoredValue.NumericalValue(900)
    }

    @Test
    fun `holds a flag true only when it held for the whole bucket`() {
        // Given
        val held = point(min = 1.0, avg = 1.0, max = 1.0, last = 1.0)
        val dipped = point(min = 0.0, avg = 0.9, max = 1.0, last = 1.0)

        // When
        val heldValue = MonitorMetrics.collapse(held, MetricId.NETWORK_UP)
        val dippedValue = MonitorMetrics.collapse(dipped, MetricId.NETWORK_UP)

        // Then
        heldValue shouldBe MonitoredValue.ConditionalValue(true)
        dippedValue shouldBe MonitoredValue.ConditionalValue(false)
    }

    private fun point(min: Double, avg: Double, max: Double, last: Double) = MetricHistoryPoint(
        timestamp = at("2026-09-12T10:00:00Z"),
        samples = 5,
        min = min,
        avg = avg,
        max = max,
        last = last
    )
}
