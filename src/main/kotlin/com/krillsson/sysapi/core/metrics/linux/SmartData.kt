package com.krillsson.sysapi.core.metrics.linux

import com.fasterxml.jackson.annotation.JsonProperty
data class SmartData(
    /*

    Some of these values are not passed all the time, so they need to be verified before usage

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


