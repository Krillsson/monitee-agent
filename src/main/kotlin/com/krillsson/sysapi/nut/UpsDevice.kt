package com.krillsson.sysapi.nut

import com.krillsson.sysapi.bash.Upsc.Test
import kotlin.text.toIntOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class UpsDevice(
    val name: String,
    val id: String,
    val description: String,
    val manufacturer: String?,
    val model: String?,
    val serial: String?,
    val type: String?,
    val firmwareVersion: String?,
    val driverName: String?,
    val driverVersion: String?,
    val realPowerNominalWatts: Int?,
    val powerNominalVA: Int?,
    val battery: Battery,
    val outlets: List<Outlet>,
    val test: UpsDevice.Test,
    val metrics: Metrics
) {

    enum class Status(val description: String) {
        OnLine("On line (mains is present)"),
        OnBattery("On battery (mains is not present)"),
        LowBattery("Low battery"),
        HighBattery("High battery"),
        ReplaceBattery("The battery needs to be replaced"),
        Charging("The battery is charging"),
        Discharging("The battery is discharging (inverter is providing load power)"),
        Bypass("UPS bypass circuit is active -- no battery protection is available"),
        Calibrating("UPS is currently performing runtime calibration (on battery)"),
        Offline("UPS is offline and is not supplying power to the load"),
        Overload("UPS is overloaded"),
        Trimming("UPS is trimming incoming voltage (called 'buck' in some hardware)"),
        Boosting("UPS is boosting incoming voltage"),
        ForcedShutdown("Forced Shutdown (restricted use)"),
        Unknown("");

        // this weirdness is because of GraphQL
        val status = this
    }

    data class Test(
        val testInterval: Duration?,
        val result: String?,
    ) {
        val testIntervalSeconds = testInterval?.inWholeSeconds
    }

    data class Metrics(
        val id: String,
        val batteryMetrics: BatteryMetrics?,
        val inputMetrics: InputMetrics?,
        val outputMetrics: OutputMetrics?,
        val loadPercent: Int?,
        val realPowerLoadWatts: Int?,
        val powerLoadVA: Int?,
        val upsStatus: List<Status>,
    ) {
        data class BatteryMetrics(
            val capacity: Float?,
            val chargePercent: Int?,
            val runtime: Duration?,
            val voltage: Float?,
            val voltageNominal: Float?,
            val chargerStatus: String?,
        ) {
            val runtimeSeconds = runtime?.inWholeSeconds
        }

        data class InputMetrics(
            val current: Float?,
            val frequency: Float?,
            val voltage: Float?,
        )

        data class OutputMetrics(
            val current: Float?,
            val frequency: Float?,
            val powerFactor: Float?,
            val voltage: Float?,
        )
    }

    data class Battery(
        val voltageNominal: Float?,
        val chargerType: String?,
        val type: String?,
    )

    data class Outlet(
        val id: Int?,
        val desc: String?,
        val status: String?,
        val switchable: String?,
        val delayShutdown: Duration?,
        val delayStart: Duration?
    ) {
        val delayShutdownSeconds: Long? = delayShutdown?.inWholeSeconds
        val delayStartSeconds: Long? = delayStart?.inWholeSeconds
    }
}