package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.ups.UpsDevice
import java.time.Instant

object UpsMetricExtractor {

    fun upsDevices(metrics: List<UpsDevice.Metrics>, timestamp: Instant): List<MetricSample> =
        metrics.flatMap { ups ->
            buildList {
                add(
                    sample(
                        MetricId.UPS_OPERATING_NORMALLY,
                        ups.id,
                        timestamp,
                        ups.isOperatingNormally().asFlag()
                    )
                )
                ups.loadPercent?.let { add(sample(MetricId.UPS_LOAD_PERCENT, ups.id, timestamp, it.toDouble())) }
                ups.realPowerLoadWatts?.let { add(sample(MetricId.UPS_LOAD_WATTS, ups.id, timestamp, it.toDouble())) }
                ups.powerLoadVA?.let { add(sample(MetricId.UPS_LOAD_VA, ups.id, timestamp, it.toDouble())) }
                ups.batteryMetrics?.chargePercent?.let {
                    add(sample(MetricId.UPS_BATTERY_CHARGE_PERCENT, ups.id, timestamp, it.toDouble()))
                }
                ups.batteryMetrics?.runtimeSeconds?.let {
                    add(sample(MetricId.UPS_BATTERY_RUNTIME_SECONDS, ups.id, timestamp, it.toDouble()))
                }
                ups.batteryMetrics?.voltage?.let {
                    add(sample(MetricId.UPS_BATTERY_VOLTAGE, ups.id, timestamp, it.toDouble()))
                }
                ups.inputMetrics?.voltage?.let {
                    add(sample(MetricId.UPS_INPUT_VOLTAGE, ups.id, timestamp, it.toDouble()))
                }
                ups.outputMetrics?.voltage?.let {
                    add(sample(MetricId.UPS_OUTPUT_VOLTAGE, ups.id, timestamp, it.toDouble()))
                }
            }
        }
}
