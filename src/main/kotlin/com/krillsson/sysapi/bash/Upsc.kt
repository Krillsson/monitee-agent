package com.krillsson.sysapi.bash

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import kotlin.getValue
import kotlin.text.toFloatOrNull
import kotlin.text.toIntOrNull

@Component
class Upsc {
    private val logger by logger()

    fun supportedBySystem(): Boolean {
        return (Bash.checkIfCommandExists("upsc").getOrNull() ?: false)
    }

    fun upsDevices(host: String, port: Int? = null): ListUpsDevicesOutput {
        val portNumber = port?.let { ":$port" }.orEmpty()
        val result = Bash.executeToText("upsc -L $host$portNumber")
        val devices = result.getOrNull().orEmpty()
            .lines()
            .filter { it.contains(":") }
            .map { it.split(":", limit = 2) }
            .map { it[0].trim() to it[1].trim() }
            .map { (name, description) ->
                ListUpsDevicesOutput.UpsDevice(name = name, description = description)
            }
        return ListUpsDevicesOutput(devices)
    }

    fun queryUpsDevice(name: String, host: String, port: Int? = null ): QueryUpsDevicesOutput {
        val portNumber = port?.let { ":$port" }.orEmpty()
        val result = Bash.executeToText("upsc $host$portNumber")
        val data = result.getOrNull().orEmpty()
            .lines()
            .filter { it.contains(":") }
            .map { it.split(":", limit = 2) }
            .associate { it[0].trim() to it[1].trim() }

        return parse(data)
    }

    fun parse(raw: Map<String, String>): QueryUpsDevicesOutput {
        return QueryUpsDevicesOutput(
            batteryCharge = raw["battery.charge"]?.toIntOrNull(),
            batteryRuntime = raw["battery.runtime"]?.toIntOrNull(),
            inputVoltage = raw["input.voltage"]?.toFloatOrNull(),
            outputVoltage = raw["output.voltage"]?.toFloatOrNull(),
            upsStatus = raw["ups.status"]
        )
    }

    data class QueryUpsDevicesOutput (
        val batteryCharge: Int?,
        val batteryRuntime: Int?,
        val inputVoltage: Float?,
        val outputVoltage: Float?,
        val upsStatus: String?
    )

    data class ListUpsDevicesOutput(
        val devices: List<UpsDevice>
    ) {
        data class UpsDevice(
            val name: String,
            val description: String,
        )
    }

}