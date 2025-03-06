package com.krillsson.sysapi.core.metrics.windows

import com.krillsson.sysapi.core.metrics.defaultimpl.DefaultDiskSensors
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.COM.WbemcliUtil
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import oshi.hardware.HWDiskStore
import oshi.util.platform.windows.WmiQueryHandler

@Lazy
@Component
open class WindowsDiskSensors(private val monitorManager: OHMManager) : DefaultDiskSensors() {
    override fun getDiskTemperature(hwDiskStore: HWDiskStore): Double? {
        return DriveTemperatureService.getDriveTemperature(hwDiskStore)?.toDouble()
    }
}


object DriveTemperatureService {

    private val logger = LoggerFactory.getLogger(DriveTemperatureService::class.java)

    private const val WMI_NAMESPACE = "ROOT\\WMI"
    private const val WMI_QUERY = "MSStorageDriver_ATAPISmartData"
    private const val TEMPERATURE_ATTRIBUTE = 0xc2 // SMART attribute ID for temperature

    enum class Properties {
        InstanceName,
        VendorSpecific
    }

    fun getDriveTemperature(disk: HWDiskStore): Int? {
        // Query WMI for SMART data
        var comInit = false
        var result: Int? = null
        val queryHandler = WmiQueryHandler.createInstance()
        try {
            comInit = queryHandler.initCOM()
            val query = WbemcliUtil.WmiQuery<Properties>(WMI_NAMESPACE, WMI_QUERY, Properties::class.java)
            val results: WbemcliUtil.WmiResult<Properties> = queryHandler.queryWMI(query)

            logger.info("Fetched ${results.resultCount} SMART data entries from WMI.")
            for (i in 0..results.resultCount) {
                val instanceName = results.getValue(Properties.InstanceName, i) as? String
                val vendorSpecific = results.getValue(Properties.VendorSpecific, i) as? ByteArray ?: continue

                logger.info("Processing SMART data entry #$i: InstanceName='$instanceName'")

                if (instanceName != null && instanceName.contains(disk.serial)) {
                    logger.info("InstanceName matches disk serial. Checking temperature attribute...")

                    val temperature = extractTemperature(vendorSpecific)
                    if (temperature != null) {
                        logger.info("Temperature attribute found! Value: $temperature°C")
                        result = temperature
                    } else {
                        logger.warn("Temperature attribute (0xC2) not found in SMART data.")
                    }
                } else {
                    logger.info("InstanceName does not match disk serial. Skipping...")
                }
            }
        } catch (e: COMException) {
            logger.warn("COM exception ${e.message}")
        } finally {
            if(comInit) {
                queryHandler.unInitCOM()
            }
        }


        logger.warn("No matching drive found or temperature unavailable.")
        return result
    }

    private fun extractTemperature(vendorSpecific: ByteArray): Int? {
        if (vendorSpecific.size < 362) {
            logger.error("SMART data is too small (${vendorSpecific.size} bytes), expected at least 362 bytes.")
            return null
        }

        for (i in 2 until vendorSpecific.size step 12) { // SMART attributes are stored every 12 bytes
            val attributeId = vendorSpecific[i].toInt() and 0xFF // Convert unsigned byte
            if (attributeId == TEMPERATURE_ATTRIBUTE) {
                val temperature = vendorSpecific[i + 5].toInt() and 0xFF
                logger.info("Found SMART attribute 0xC2 at index $i, Temperature: $temperature°C")
                return temperature
            }
        }
        return null
    }
}
