package com.krillsson.sysapi.core.history.db

import com.krillsson.sysapi.ups.UpsDevice
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.seconds

fun UpsMetricsHistoryEntity.asUpsDeviceMetrics(): UpsDevice.Metrics = UpsDevice.Metrics(
    id = upsId,
    batteryMetrics = metrics.batteryMetrics?.asUpsDeviceBatteryMetrics(),
    inputMetrics = metrics.inputMetrics?.asUpsDeviceInputMetrics(),
    outputMetrics = metrics.outputMetrics?.asUpsDeviceOutputMetrics(),
    loadPercent = metrics.loadPercent,
    realPowerLoadWatts = metrics.realPowerLoadWatts,
    powerLoadVA = metrics.powerLoadVA,
    upsStatus = metrics.upsStatus?.split(",")?.mapNotNull { UpsDevice.Status.values().find { s -> s.name == it } } ?: emptyList()
)

fun UpsDevice.Metrics.asUpsMetricsHistoryEntity(upsId: String, timestamp: Instant): UpsMetricsHistoryEntity = UpsMetricsHistoryEntity(
    id = UUID.randomUUID(),
    upsId = upsId,
    timestamp = timestamp,
    metrics = UpsMetricsEntity(
        batteryMetrics = batteryMetrics?.asEntity(),
        inputMetrics = inputMetrics?.asEntity(),
        outputMetrics = outputMetrics?.asEntity(),
        loadPercent = loadPercent,
        realPowerLoadWatts = realPowerLoadWatts,
        powerLoadVA = powerLoadVA,
        upsStatus = upsStatus.joinToString(",") { it.name }
    )
)

fun BatteryMetricsEntity.asUpsDeviceBatteryMetrics(): UpsDevice.Metrics.BatteryMetrics = UpsDevice.Metrics.BatteryMetrics(
    capacity = batteryCapacity,
    chargePercent = batteryChargePercent,
    runtime = batteryRuntimeSeconds?.let { it.seconds },
    voltage = batteryVoltage,
    voltageNominal = batteryVoltageNominal,
    chargerStatus = batteryChargerStatus
)

fun UpsDevice.Metrics.BatteryMetrics.asEntity(): BatteryMetricsEntity = BatteryMetricsEntity(
    batteryCapacity = capacity,
    batteryChargePercent = chargePercent,
    batteryRuntimeSeconds = runtime?.inWholeSeconds,
    batteryVoltage = voltage,
    batteryVoltageNominal = voltageNominal,
    batteryChargerStatus = chargerStatus
)

fun InputMetricsEntity.asUpsDeviceInputMetrics(): UpsDevice.Metrics.InputMetrics = UpsDevice.Metrics.InputMetrics(
    current = inputCurrent,
    frequency = inputFrequency,
    voltage = inputVoltage
)

fun UpsDevice.Metrics.InputMetrics.asEntity(): InputMetricsEntity = InputMetricsEntity(
    inputCurrent = current,
    inputFrequency = frequency,
    inputVoltage = voltage
)

fun OutputMetricsEntity.asUpsDeviceOutputMetrics(): UpsDevice.Metrics.OutputMetrics = UpsDevice.Metrics.OutputMetrics(
    current = outputCurrent,
    frequency = outputFrequency,
    powerFactor = outputPowerFactor,
    voltage = outputVoltage
)

fun UpsDevice.Metrics.OutputMetrics.asEntity(): OutputMetricsEntity = OutputMetricsEntity(
    outputCurrent = current,
    outputFrequency = frequency,
    outputPowerFactor = powerFactor,
    outputVoltage = voltage
)
