package com.krillsson.sysapi.ups

import com.krillsson.sysapi.bash.Upsc
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.TimeUnit

@Service
class UpsService(
    private val upsc: Upsc,
    configuration: YAMLConfigFile
) {

    private val config = configuration.ups

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val error: RuntimeException) : Status()
    }

    private val upsDeviceMetricsSink = Sinks.many()
        .replay()
        .latest<List<UpsDevice.Metrics>>()

    val logger by logger()

    private val supportedBySystem: Boolean = upsc.supportedBySystem()

    fun status(): Status {
        return when {
            !config.enabled -> Status.Disabled
            !supportedBySystem -> Status.Unavailable(RuntimeException("upsc command was not found"))
            else -> Status.Available
        }
    }

    fun upsDeviceByName(name: String): UpsDevice? {
        return upsDevices().find { it.name == name }
    }

    fun upsDevicesMetrics(): Flux<List<UpsDevice.Metrics>> {
        return upsDeviceMetricsSink.asFlux()
    }

    fun upsDevicesMetricsById(id: String): Flux<UpsDevice.Metrics> {
        return upsDevicesMetrics()
            .mapNotNull { list: List<UpsDevice.Metrics> ->
                list.firstOrNull { n -> n.id.equals(id, ignoreCase = true) }
            }
    }

    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    fun runMeasurement() {
        if (config.enabled && upsDeviceMetricsSink.currentSubscriberCount() > 0) {
            upsDeviceMetricsSink.tryEmitNext(
                upsDevices().map {
                    it.metrics
                }
            )
        }
    }

    fun upsDevices(): List<UpsDevice> {
        return when (status()) {
            Status.Available -> {
                val deviceList = upsc.upsDevices(config.host, config.port)
                deviceList.devices.map {
                    val output = upsc.queryUpsDevice(it.name, config.host, config.port)
                    with(output) {
                        UpsDevice(
                            name = it.name,
                            id = it.name,
                            description = it.description,
                            manufacturer = ups.deviceManufacturer,
                            model = ups.deviceModel,
                            serial = ups.deviceSerial,
                            type = ups.deviceType,
                            firmwareVersion = ups.firmware,
                            driverName = driver.name,
                            driverVersion = driver.version,
                            realPowerNominalWatts = ups.realPowerNominalWatts,
                            powerNominalVA = ups.powerNominalVA,
                            battery = UpsDevice.Battery(
                                voltageNominal = battery.voltageNominal,
                                chargerType = battery.chargerType,
                                type = battery.type
                            ),
                            outlets = outlets.map { outlet ->
                                UpsDevice.Outlet(
                                    id = outlet.id,
                                    desc = outlet.desc,
                                    status = outlet.status,
                                    switchable = outlet.switchable,
                                    delayShutdown = outlet.delayShutdown,
                                    delayStart = outlet.delayStart
                                )
                            },
                            test = UpsDevice.Test(test.testInterval, test.testResult),
                            metrics = UpsDevice.Metrics(
                                id = it.name,
                                batteryMetrics = UpsDevice.Metrics.BatteryMetrics(
                                    capacity = battery.capacity,
                                    chargePercent = battery.chargePercent,
                                    runtime = battery.runtime,
                                    voltage = battery.voltage,
                                    voltageNominal = battery.voltageNominal,
                                    chargerStatus = battery.chargerStatus
                                ),
                                inputMetrics = UpsDevice.Metrics.InputMetrics(
                                    current = input.current,
                                    frequency = input.frequency,
                                    voltage = input.voltage
                                ),
                                outputMetrics = UpsDevice.Metrics.OutputMetrics(
                                    current = this.output.current,
                                    frequency = this.output.frequency,
                                    powerFactor = this.output.powerFactor,
                                    voltage = this.output.voltage
                                ),
                                loadPercent = ups.loadPercent,
                                realPowerLoadWatts = ups.realPowerLoadWatts,
                                powerLoadVA = ups.powerLoadVA,
                                upsStatus = ups.status.map { UpsDevice.Status.valueOf(it.name) }
                            )
                        )
                    }
                }
            }

            else -> emptyList()
        }
    }
}