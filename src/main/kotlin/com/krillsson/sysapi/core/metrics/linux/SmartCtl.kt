package com.krillsson.sysapi.core.metrics.linux

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.systemd.Bash
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component

@Component
class SmartCtl(val mapper: ObjectMapper) {

    companion object {
        private const val COMMAND = "smartctl"
        private const val QUERY_SMART_DATA = "$COMMAND -jA %s"
    }

    private val logger by logger()

    fun supportsCommand() = Bash.checkIfCommandExists(COMMAND).getOrNull() ?: false

    fun getSmartData(deviceName: String): SmartData? {
        return try {
            val result = Bash.executeToText(QUERY_SMART_DATA.format(deviceName))
            result.getOrNull()?.convertJsonStringToSmartData()
        } catch (exception: Throwable) {
            logger.error("Unable to execute command for device $deviceName", exception)
            null
        }
    }

    private fun String.convertJsonStringToSmartData(): SmartData? {
        return try {
            mapper.readValue(this, SmartData::class.java)
        } catch (exception: Throwable) {
            logger.error("Unable to parse json", exception)
            null
        }
    }
}