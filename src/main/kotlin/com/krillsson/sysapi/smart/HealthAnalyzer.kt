package com.krillsson.sysapi.smart

import org.springframework.stereotype.Component

enum class HealthStatus { HEALTHY, WARNING, FAILING, CRITICAL }
data class DeviceHealth(val status: HealthStatus, val messages: List<String>)

interface HealthAnalyzer<T : SmartData> {
    fun analyze(device: T): DeviceHealth
}

@Component
class HealthAnalyzerService(
    private val ssdHealthAnalyzer: SimpleSsdHealthAnalyzer,
    private val hddHealthAnalyzer: SimpleHddHealthAnalyzer,
    private val nvmeHealthAnalyzer: SimpleNvmeHealthAnalyzer
) {
    fun analyze(device: SmartData): DeviceHealth {
        return when (device) {
            is SmartData.Hdd -> hddHealthAnalyzer.analyze(device)
            is SmartData.Nvme -> nvmeHealthAnalyzer.analyze(device)
            is SmartData.SataSsd -> ssdHealthAnalyzer.analyze(device)
        }
    }
}

@Component
class SimpleHddHealthAnalyzer : HealthAnalyzer<SmartData.Hdd> {
    override fun analyze(device: SmartData.Hdd): DeviceHealth {
        val messages = mutableListOf<String>()
        var status = HealthStatus.HEALTHY

        device.reallocatedSectors?.let {
            when {
                it > 100 -> {
                    status = HealthStatus.CRITICAL
                    messages.add("High reallocated sectors: $it")
                }
                it > 0 -> {
                    status = maxOfStatus(status, HealthStatus.WARNING)
                    messages.add("Reallocated sectors: $it")
                }
                else -> {}
            }
        }

        device.pendingSectors?.let {
            if (it > 0) {
                status = HealthStatus.FAILING
                messages.add("Pending sectors present: $it - backup immediately")
            }
        }

        device.uncorrectableSectors?.let {
            if (it > 0) {
                status = HealthStatus.CRITICAL
                messages.add("Uncorrectable sectors: $it")
            }
        }

        device.udmaCrcErrors?.let {
            if (it > 100) {
                messages.add("Many UDMA CRC errors (check cable): $it")
                status = maxOfStatus(status, HealthStatus.WARNING)
            }
        }

        if (messages.isEmpty()) messages.add("No immediate SMART warnings detected")
        return DeviceHealth(status, messages)
    }

    private fun maxOfStatus(a: HealthStatus, b: HealthStatus): HealthStatus {
        val order = listOf(HealthStatus.HEALTHY, HealthStatus.WARNING, HealthStatus.FAILING, HealthStatus.CRITICAL)
        return if (order.indexOf(a) >= order.indexOf(b)) a else b
    }
}

@Component
class SimpleSsdHealthAnalyzer : HealthAnalyzer<SmartData.SataSsd> {
    override fun analyze(device: SmartData.SataSsd): DeviceHealth {
        val messages = mutableListOf<String>()
        var status = HealthStatus.HEALTHY

        device.percentageUsed?.let {
            // percent used meaning depends on vendor (often "life left" vs used). we assume 0..100 used %
            if (it >= 95) {
                status = HealthStatus.WARNING
                messages.add("SSD near rated endurance: $it%")
            }
            if (it >= 100) {
                status = HealthStatus.CRITICAL
                messages.add("SSD reports 100% life used")
            }
        }

        device.uncorrectableErrors?.let {
            if (it > 0) {
                status = HealthStatus.CRITICAL
                messages.add("Uncorrectable errors: $it - risk of data loss")
            }
        }

        device.totalWriteGiB?.let {
            messages.add("Total writes: ${it} GiB")
        }

        if (messages.isEmpty()) messages.add("No immediate SSD SMART warnings detected")
        return DeviceHealth(status, messages)
    }
}

@Component
class SimpleNvmeHealthAnalyzer : HealthAnalyzer<SmartData.Nvme> {
    override fun analyze(device: SmartData.Nvme): DeviceHealth {
        val messages = mutableListOf<String>()
        var status = HealthStatus.HEALTHY

        device.percentageUsed?.let {
            if (it >= 95) {
                messages.add("NVMe near rated endurance: $it%")
                status = HealthStatus.WARNING
            }
            if (it >= 100) {
                messages.add("NVMe reports 100% life used")
                status = HealthStatus.CRITICAL
            }
        }

        device.mediaErrors?.let {
            if (it > 0) {
                messages.add("Media errors: $it")
                status = HealthStatus.CRITICAL
            }
        }

        device.numErrLogEntries?.let {
            if (it > 0) messages.add("Error log entries: $it")
        }

        if (messages.isEmpty()) messages.add("No immediate NVMe SMART warnings detected")
        return DeviceHealth(status, messages)
    }
}