package com.krillsson.sysapi.smart

import com.krillsson.sysapi.bash.SmartCtl
import org.springframework.stereotype.Service

@Service
class SmartManager(
    private val smartCtl: SmartCtl,
    private val smartParser: SmartParser,
    private val healthAnalyzer: HealthAnalyzerService
) {

    fun supportsSmartCommand() = smartCtl.supportsCommand()
    fun getSmartData(device: String): SmartData? {
        val output = smartCtl.getSmartData(device) ?: return null

        return smartParser.parse(device, output)
    }

    fun health(data: SmartData): DeviceHealth {
        return healthAnalyzer.analyze(data)
    }

}
