package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.ups.UpsDevice
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UpsMetricExtractorTest {

    private val timestamp = at("2026-09-12T10:00:00Z")

    @Test
    fun `sets the operating normally flag only while every status is normal`() {
        // Given
        val online = upsMetrics(statuses = listOf(UpsDevice.Status.OnLine))
        val onBattery = upsMetrics(statuses = listOf(UpsDevice.Status.OnBattery))

        // When
        val onlineSamples = UpsMetricExtractor.upsDevices(listOf(online), timestamp)
        val onBatterySamples = UpsMetricExtractor.upsDevices(listOf(onBattery), timestamp)

        // Then
        onlineSamples.valueOf(MetricId.UPS_OPERATING_NORMALLY, "ups-1") shouldBe 1.0
        onBatterySamples.valueOf(MetricId.UPS_OPERATING_NORMALLY, "ups-1") shouldBe 0.0
    }

    @Test
    fun `records a nullable reading as no sample rather than a zero`() {
        // Given
        val metrics = upsMetrics(loadPercent = null, realPowerLoadWatts = null)

        // When
        val samples = UpsMetricExtractor.upsDevices(listOf(metrics), timestamp)

        // Then
        samples.map { it.metric } shouldContainExactly listOf(MetricId.UPS_OPERATING_NORMALLY)
        samples.map { it.metric } shouldNotContain MetricId.UPS_LOAD_PERCENT
    }

    @Test
    fun `records the readings a device does report`() {
        // Given
        val metrics = upsMetrics(loadPercent = 42, realPowerLoadWatts = 120)

        // When
        val samples = UpsMetricExtractor.upsDevices(listOf(metrics), timestamp)

        // Then
        samples.valueOf(MetricId.UPS_LOAD_PERCENT, "ups-1") shouldBe 42.0
        samples.valueOf(MetricId.UPS_LOAD_WATTS, "ups-1") shouldBe 120.0
    }

    private fun upsMetrics(
        id: String = "ups-1",
        loadPercent: Int? = 50,
        realPowerLoadWatts: Int? = 100,
        statuses: List<UpsDevice.Status> = listOf(UpsDevice.Status.OnLine)
    ) = UpsDevice.Metrics(
        id = id,
        batteryMetrics = null,
        inputMetrics = null,
        outputMetrics = null,
        loadPercent = loadPercent,
        realPowerLoadWatts = realPowerLoadWatts,
        powerLoadVA = null,
        upsStatus = statuses
    )
}
