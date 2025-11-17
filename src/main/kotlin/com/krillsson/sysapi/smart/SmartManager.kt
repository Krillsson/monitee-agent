package com.krillsson.sysapi.smart

import com.krillsson.sysapi.bash.SmartCtl
import org.springframework.stereotype.Service

@Service
class SmartManager(
    private val smartCtl: SmartCtl,
    private val smartParser: SmartParser
) {
    fun getSmartData(device: String): StorageDevice? {
        val output = smartCtl.getSmartData(device) ?: return null

        return smartParser.parse(device, output)
    }




}
