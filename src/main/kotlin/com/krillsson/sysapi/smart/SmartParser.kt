package com.krillsson.sysapi.smart

import com.krillsson.sysapi.bash.SmartCtl
import org.springframework.stereotype.Component

@Component
class SmartParser(
    private val healthAnalyzerService: HealthAnalyzerService
) {

    private enum class DriveType { HDD, SATA_SSD, NVME }

    fun parse(deviceName: String, json: SmartCtl.Output): SmartData {
        val attrs = parseAttributes(json)
        val driveType = detectDriveType(json, attrs)
        return when (driveType) {
            DriveType.NVME -> parseNvme(deviceName, json)
            DriveType.SATA_SSD -> parseSataSsd(deviceName, json, attrs)
            DriveType.HDD -> parseHdd(deviceName, json, attrs)
        }
    }

    private fun parseAttributes(json: SmartCtl.Output): Map<Int, SmartData.SmartAttribute> {
        val ata = json.ataSmartAttributes ?: return emptyMap()
        return ata.table.associate { t ->
            t.id to SmartData.SmartAttribute(
                id = t.id,
                name = t.name,
                raw = t.raw.value,
                normalized = t.value,
                worst = t.worst
            )
        }
    }

    private fun detectDriveType(json: SmartCtl.Output, attrs: Map<Int, SmartData.SmartAttribute>): DriveType {
        if (json.nvmeSmartHealthInformationLog != null) return DriveType.NVME

        return when {
            attrs.containsKey(231) || attrs.containsKey(177) || attrs.containsKey(179) -> DriveType.SATA_SSD
            attrs.containsKey(5) || attrs.containsKey(197) -> DriveType.HDD
            else -> DriveType.HDD
        }
    }

    private fun parseNvme(deviceName: String, json: SmartCtl.Output): SmartData.Nvme {
        val log = json.nvmeSmartHealthInformationLog
        return SmartData.Nvme(
            name = deviceName,
            temperatureCelsius = json.temperature.current,
            powerOnHours = json.powerOnTime?.hours?.toLong(),
            powerCycleCount = json.powerCycleCount,
            rawAttributes = emptyMap(),
            percentageUsed = log?.percentage_used,
            dataUnitsRead = log?.data_units_read,
            dataUnitsWritten = log?.data_units_written,
            mediaErrors = log?.media_errors,
            numErrLogEntries = log?.num_err_log_entries,
            unsafeShutdowns = log?.unsafe_shutdowns,
            controllerBusyTimeMinutes = log?.controller_busy_time,
            vendorData = emptyMap()
        )
    }

    private fun parseHdd(
        deviceName: String,
        json: SmartCtl.Output,
        attrs: Map<Int, SmartData.SmartAttribute>
    ): SmartData.Hdd {
        return SmartData.Hdd(
            name = deviceName,
            temperatureCelsius = json.temperature.current,
            powerOnHours = json.powerOnTime?.hours?.toLong(),
            powerCycleCount = json.powerCycleCount,
            rawAttributes = attrs,

            reallocatedSectors = attrs[5]?.raw,
            pendingSectors = attrs[197]?.raw,
            uncorrectableSectors = attrs[198]?.raw,
            offlineUncorrectable = attrs[198]?.raw,
            spinRetryCount = attrs[10]?.raw,
            seekErrorRate = attrs[7]?.raw,
            udmaCrcErrors = attrs[199]?.raw
        )
    }

    private fun parseSataSsd(
        deviceName: String,
        json: SmartCtl.Output,
        attrs: Map<Int, SmartData.SmartAttribute>
    ): SmartData.SataSsd {

        fun lbaToGiB(value: Long): Long = (value * 512L) / (1024L * 1024L * 1024L)

        return SmartData.SataSsd(
            name = deviceName,
            temperatureCelsius = json.temperature.current,
            powerOnHours = json.powerOnTime?.hours?.toLong(),
            powerCycleCount = json.powerCycleCount,
            rawAttributes = attrs,

            percentageUsed = (
                    attrs[231]?.normalized
                        ?: attrs[202]?.normalized
                        ?: attrs[230]?.normalized
                    ),

            wearLevelingCount =
                attrs[177]?.raw?.toInt()
                    ?: attrs[179]?.raw?.toInt(), // Phison/Crucial layout

            availableReservedSpace =
                attrs[170]?.normalized
                    ?: attrs[171]?.normalized,

            totalWriteGiB = attrs[241]?.raw?.let { lbaToGiB(it) },
            totalReadGiB = attrs[242]?.raw?.let { lbaToGiB(it) },

            mediaErrors = attrs[187]?.raw ?: attrs[198]?.raw,
            uncorrectableErrors = attrs[187]?.raw ?: attrs[198]?.raw,
            udmaCrcErrors = attrs[199]?.raw
        )
    }

}