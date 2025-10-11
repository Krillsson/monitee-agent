package com.krillsson.sysapi.nut

data class UpsDevice(
    val name: String,
    val description: String,
    val metrics: Metrics
) {
    data class Metrics (
        val batteryCharge: Int?,
        val batteryRuntime: Int?,
        val inputVoltage: Float?,
        val outputVoltage: Float?,
        val upsStatus: String?
    )
}