package com.krillsson.sysapi.nut

import com.krillsson.sysapi.bash.Upsc
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import kotlin.getValue

@Service
class NutUpsService(
    private val upsc: Upsc,
    configuration: YAMLConfigFile
) {

    private val config = configuration.ups

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val error: RuntimeException) : Status()
    }

    val logger by logger()

    private val supportedBySystem: Boolean = upsc.supportedBySystem()

    fun status(): Status {
        return when {
            !config.enabled -> Status.Disabled
            !supportedBySystem -> Status.Unavailable(RuntimeException("systemctl or journalctl command was not found"))
            else -> Status.Available
        }
    }


    fun upsDevices(): List<UpsDevice> {
        return when(status()){
            Status.Available -> {
                val deviceList = upsc.upsDevices(config.host, config.port)
                deviceList.devices.map {
                    val output = upsc.queryUpsDevice(it.name, config.host, config.port)
                    UpsDevice(
                        it.name,
                        it.description,
                        with(output){
                            UpsDevice.Metrics(
                                batteryCharge,
                                batteryRuntime,
                                inputVoltage,
                                outputVoltage,
                                upsStatus
                            )
                        }
                    )
                }
            }
            else -> emptyList()
        }
    }
}