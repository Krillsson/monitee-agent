package com.krillsson.sysapi.bash

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component

@Component
class SmartCtl(val mapper: ObjectMapper) {

    companion object {
        private const val COMMAND = "smartctl"
        private const val QUERY_SMART_DATA = "$COMMAND -jA %s"
    }

    private val logger by logger()

    private var ignoredDevices = mutableListOf<String>()

    fun supportsCommand() = Bash.checkIfCommandExists(COMMAND).getOrNull() ?: false

    fun getSmartData(deviceName: String): Output? {
        return try {
            if (!ignoredDevices.contains(deviceName)) {
                val result = Bash.executeToText(QUERY_SMART_DATA.format(deviceName))
                val json = result.getOrNull()?.convertJsonStringToSmartData(deviceName)
                json
            } else {
                null
            }

        } catch (exception: Throwable) {
            logger.error("Unable to execute command for device $deviceName", exception)
            null
        }
    }

    private fun String.convertJsonStringToSmartData(deviceName: String): Output? {
        return try {
            mapper.readValue(this, Output::class.java)
        } catch (exception: Throwable) {
            logger.error("Ignoring device. Unable to parse json for $deviceName ${exception.message}")
            ignoredDevices.add(deviceName)
            null
        }
    }

    data class Output(
        /*

        Some of these values are not passed all the time,
        so they need to be verified before usage and made nullable,
        we are only interested in temperature for now.

        @JsonProperty("ata_smart_attributes")
        val ataSmartAttributes: AtaSmartAttributes,
        @JsonProperty("device")
        val device: Device,
        @JsonProperty("json_format_version")
        val jsonFormatVersion: List<Int>,
        @JsonProperty("local_time")
        val localTime: LocalTime,
        @JsonProperty("power_cycle_count")
        val powerCycleCount: Int,
        @JsonProperty("power_on_time")
        val powerOnTime: PowerOnTime,
        @JsonProperty("smartctl")
        val smartctl: Smartctl,

        */
        @JsonProperty("temperature")
        val temperature: Temperature
    ) {
        data class AtaSmartAttributes(
            @JsonProperty("revision")
            val revision: Int,
            @JsonProperty("table")
            val table: List<Table>
        ) {
            data class Table(
                @JsonProperty("flags")
                val flags: Flags,
                @JsonProperty("id")
                val id: Int,
                @JsonProperty("name")
                val name: String,
                @JsonProperty("raw")
                val raw: Raw,
                @JsonProperty("thresh")
                val thresh: Int,
                @JsonProperty("value")
                val value: Int,
                @JsonProperty("when_failed")
                val whenFailed: String,
                @JsonProperty("worst")
                val worst: Int
            ) {
                data class Flags(
                    @JsonProperty("auto_keep")
                    val autoKeep: Boolean,
                    @JsonProperty("error_rate")
                    val errorRate: Boolean,
                    @JsonProperty("event_count")
                    val eventCount: Boolean,
                    @JsonProperty("performance")
                    val performance: Boolean,
                    @JsonProperty("prefailure")
                    val prefailure: Boolean,
                    @JsonProperty("string")
                    val string: String,
                    @JsonProperty("updated_online")
                    val updatedOnline: Boolean,
                    @JsonProperty("value")
                    val value: Int
                )

                data class Raw(
                    @JsonProperty("string")
                    val string: String,
                    @JsonProperty("value")
                    val value: Long
                )
            }
        }

        data class Device(
            @JsonProperty("info_name")
            val infoName: String,
            @JsonProperty("name")
            val name: String,
            @JsonProperty("protocol")
            val protocol: String,
            @JsonProperty("type")
            val type: String
        )

        data class LocalTime(
            @JsonProperty("asctime")
            val asctime: String,
            @JsonProperty("time_t")
            val timeT: Int
        )

        data class PowerOnTime(
            @JsonProperty("hours")
            val hours: Int
        )

        data class Smartctl(
            @JsonProperty("argv")
            val argv: List<String>,
            @JsonProperty("build_info")
            val buildInfo: String,
            @JsonProperty("drive_database_version")
            val driveDatabaseVersion: DriveDatabaseVersion,
            @JsonProperty("exit_status")
            val exitStatus: Int,
            @JsonProperty("platform_info")
            val platformInfo: String,
            @JsonProperty("pre_release")
            val preRelease: Boolean,
            @JsonProperty("svn_revision")
            val svnRevision: String,
            @JsonProperty("version")
            val version: List<Int>
        ) {
            data class DriveDatabaseVersion(
                @JsonProperty("string")
                val string: String
            )
        }

        data class Temperature(
            @JsonProperty("current")
            val current: Int
        )
    }
}
