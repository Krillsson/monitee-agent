package com.krillsson.sysapi.bash

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import kotlin.getValue
import kotlin.text.toFloatOrNull
import kotlin.text.toIntOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


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
        val result = Bash.executeToText("upsc $name@$host$portNumber")
        val data = result.getOrNull().orEmpty()
            .lines()
            .filter { it.contains(":") }
            .map { it.split(":", limit = 2) }
            .associate { it[0].trim() to it[1].trim() }

        return parse(data)
    }

    data class QueryUpsDevicesOutput(
        val battery: Battery,
        val input: Input,
        val output: Output,
        val outlets: List<Outlet>,
        val ups: Ups,
        val driver: Driver,
        val test: Test,
    )

    fun parse(raw: Map<String, String>): QueryUpsDevicesOutput {

        // Discover numbered outlets
        val outletRegex = Regex("""outlet\.(\d+)\.id""")
        val outletNumbers = raw.keys.mapNotNull { key ->
            outletRegex.find(key)?.groupValues?.get(1)?.toIntOrNull()
        }.distinct()

        val numberedOutlets = outletNumbers.map { index ->
            val prefix = "outlet.$index."
            Outlet(
                id = raw["${prefix}id"]?.toIntOrNull(),
                desc = raw["${prefix}desc"],
                status = raw["${prefix}status"],
                switchable = raw["${prefix}switchable"],
                autoswitchChargeLow = raw["${prefix}autoswitch.charge.low"]?.toIntOrNull(),
                delayShutdown = raw["${prefix}delay.shutdown"]?.toIntOrNull()?.seconds,
                delayStart = raw["${prefix}delay.start"]?.toIntOrNull()?.seconds
            )
        }

        // Handle unnumbered outlet if present
        val unnumberedOutlet = if (raw.containsKey("outlet.id")) {
            Outlet(
                id = raw["outlet.id"]?.toIntOrNull(),
                desc = raw["outlet.desc"],
                status = raw["outlet.status"],
                switchable = raw["outlet.switchable"],
                autoswitchChargeLow = raw["outlet.autoswitch.charge.low"]?.toIntOrNull(),
                delayShutdown = raw["outlet.delay.shutdown"]?.toIntOrNull()?.seconds,
                delayStart = raw["outlet.delay.start"]?.toIntOrNull()?.seconds
            )
        } else null

        val outlets = if (unnumberedOutlet != null) {
            listOf(unnumberedOutlet) + numberedOutlets
        } else {
            numberedOutlets
        }

        val battery = Battery(
            capacity = raw["battery.capacity"]?.toFloatOrNull(),
            chargePercent = raw["battery.charge"]?.toIntOrNull(),
            chargeLowPercent = raw["battery.charge.low"]?.toIntOrNull(),
            chargeRestart = raw["battery.charge.restart"]?.toIntOrNull(),
            chargerStatus = raw["battery.charger.status"],
            chargerType = raw["battery.charger.type"],
            energysave = raw["battery.energysave"],
            energysaveDelay = raw["battery.energysave.delay"]?.toIntOrNull()?.seconds,
            energysaveLoad = raw["battery.energysave.load"]?.toIntOrNull(),
            protection = raw["battery.protection"],
            runtime = raw["battery.runtime"]?.toIntOrNull()?.seconds,
            type = raw["battery.type"],
            voltage = raw["battery.voltage"]?.toFloatOrNull(),
            voltageNominal = raw["battery.voltage.nominal"]?.toFloatOrNull()
        )
        val input = Input(
            current = raw["input.current"]?.toFloatOrNull(),
            frequency = raw["input.frequency"]?.toFloatOrNull(),
            frequencyNominal = raw["input.frequency.nominal"]?.toFloatOrNull(),
            sensitivity = raw["input.sensitivity"],
            transferBoostLow = raw["input.transfer.boost.low"]?.toIntOrNull(),
            transferHigh = raw["input.transfer.high"]?.toIntOrNull(),
            transferLow = raw["input.transfer.low"]?.toIntOrNull(),
            transferTrimHigh = raw["input.transfer.trim.high"]?.toIntOrNull(),
            voltage = raw["input.voltage"]?.toFloatOrNull(),
            voltageNominal = raw["input.voltage.nominal"]?.toFloatOrNull()
        )
        val output = Output(
            current = raw["output.current"]?.toFloatOrNull(),
            frequency = raw["output.frequency"]?.toFloatOrNull(),
            frequencyNominal = raw["output.frequency.nominal"]?.toFloatOrNull(),
            powerFactor = raw["output.powerfactor"]?.toFloatOrNull(),
            voltage = raw["output.voltage"]?.toFloatOrNull(),
            voltageNominal = raw["output.voltage.nominal"]?.toFloatOrNull()
        )
        val ups = Ups(
            beeperStatus = raw["ups.beeper.status"],
            delayShutdown = raw["ups.delay.shutdown"]?.toIntOrNull()?.seconds,
            delayStart = raw["ups.delay.start"]?.toIntOrNull()?.seconds,
            efficiency = raw["ups.efficiency"]?.toIntOrNull(),
            firmware = raw["ups.firmware"],
            loadPercent = raw["ups.load"]?.toIntOrNull(),
            loadHighPercent = raw["ups.load.high"]?.toIntOrNull(),
            powerLoadVA = raw["ups.power"]?.toIntOrNull(),
            powerNominalVA = raw["ups.power.nominal"]?.toIntOrNull(),
            productId = raw["ups.productid"],
            realPowerLoadWatts = raw["ups.realpower"]?.toIntOrNull(),
            realPowerNominalWatts = raw["ups.realpower.nominal"]?.toIntOrNull(),
            shutdown = raw["ups.shutdown"],
            startAuto = raw["ups.start.auto"],
            startBattery = raw["ups.start.battery"],
            startReboot = raw["ups.start.reboot"],
            status = parseUpsStatus(raw["ups.status"]),
            timerShutdown = raw["ups.timer.shutdown"]?.toIntOrNull()?.seconds,
            timerStart = raw["ups.timer.start"]?.toIntOrNull()?.seconds,
            vendorId = raw["ups.vendorid"],
            deviceManufacturer = raw["device.mfr"],
            deviceModel = raw["device.model"],
            deviceSerial = raw["device.serial"],
            deviceType = raw["device.type"]
        )
        val driver = Driver(
            name = raw["driver.name"],
            version = raw["driver.version"],
            versionData = raw["driver.version.data"],
            versionUsb = raw["driver.version.usb"],
        )
        val test = Test(
            testInterval = raw["ups.test.interval"]?.toIntOrNull()?.seconds,
            testResult = raw["ups.test.result"],
        )
        return QueryUpsDevicesOutput(battery, input, output, outlets, ups, driver , test)
    }


    data class Battery(
        val capacity: Float?,
        val chargePercent: Int?,
        val chargeLowPercent: Int?,
        val chargeRestart: Int?,
        val chargerStatus: String?,
        val chargerType: String?,
        val energysave: String?,
        val energysaveDelay: Duration?,
        val energysaveLoad: Int?,
        val protection: String?,
        val runtime: Duration?,
        val type: String?,
        val voltage: Float?,
        val voltageNominal: Float?
    )

    data class Input(
        val current: Float?,
        val frequency: Float?,
        val frequencyNominal: Float?,
        val sensitivity: String?,
        val transferBoostLow: Int?,
        val transferHigh: Int?,
        val transferLow: Int?,
        val transferTrimHigh: Int?,
        val voltage: Float?,
        val voltageNominal: Float?
    )

    data class Output(
        val current: Float?,
        val frequency: Float?,
        val frequencyNominal: Float?,
        val powerFactor: Float?,
        val voltage: Float?,
        val voltageNominal: Float?
    )

    data class Outlet(
        val id: Int?,
        val desc: String?,
        val status: String?,
        val switchable: String?,
        val autoswitchChargeLow: Int?,
        val delayShutdown: Duration?,
        val delayStart: Duration?
    )

    data class Ups(
        val beeperStatus: String?,
        val delayShutdown: Duration?,
        val delayStart: Duration?,
        val efficiency: Int?,
        val firmware: String?,
        val loadPercent: Int?,
        val loadHighPercent: Int?,
        val powerLoadVA: Int?,
        val powerNominalVA: Int?,
        val productId: String?,
        val realPowerLoadWatts: Int?,
        val realPowerNominalWatts: Int?,
        val shutdown: String?,
        val startAuto: String?,
        val startBattery: String?,
        val startReboot: String?,
        val status: Set<UpsStatus>,
        val timerShutdown: Duration?,
        val timerStart: Duration?,
        val vendorId: String?,
        val deviceManufacturer: String?,
        val deviceModel: String?,
        val deviceSerial: String?,
        val deviceType: String?
    )

    data class Test(
        val testInterval: Duration?,
        val testResult: String?,
    )

    data class Driver(
        val name: String?,
        val version: String?,
        val versionData: String?,
        val versionUsb: String?,
    )

    data class ListUpsDevicesOutput(
        val devices: List<UpsDevice>
    ) {
        data class UpsDevice(
            val name: String,
            val description: String,
        )
    }

    enum class UpsStatus {
        OnLine,
        OnBattery,
        LowBattery,
        HighBattery,
        ReplaceBattery,
        Charging,
        Discharging,
        Bypass,
        Calibrating,
        Offline,
        Overload,
        Trimming,
        Boosting,
        ForcedShutdown,
        Unknown
    }

    private fun parseUpsStatus(statusString: String?): Set<UpsStatus> {
        if (statusString == null) return emptySet()
        return statusString.split(Regex("\\s+|,")).mapNotNull { code ->
            when (code) {
                "OL" -> UpsStatus.OnLine
                "OB" -> UpsStatus.OnBattery
                "LB" -> UpsStatus.LowBattery
                "HB" -> UpsStatus.HighBattery
                "RB" -> UpsStatus.ReplaceBattery
                "CHRG" -> UpsStatus.Charging
                "DISCHRG" -> UpsStatus.Discharging
                "BYPASS" -> UpsStatus.Bypass
                "CAL" -> UpsStatus.Calibrating
                "OFF" -> UpsStatus.Offline
                "OVER" -> UpsStatus.Overload
                "TRIM" -> UpsStatus.Trimming
                "BOOST" -> UpsStatus.Boosting
                "FSD" -> UpsStatus.ForcedShutdown
                else -> UpsStatus.Unknown
            }
        }.toSet()
    }

}