package com.krillsson.sysapi.smart

enum class HealthStatus { HEALTHY, WARNING, FAILING, CRITICAL }
data class DeviceHealth(val status: HealthStatus, val messages: List<String>)

interface HealthAnalyzer<T : StorageDevice> {
    fun analyze(device: T): DeviceHealth
}

class SimpleHddHealthAnalyzer : HealthAnalyzer<StorageDevice.Hdd> {
    override fun analyze(device: StorageDevice.Hdd): DeviceHealth {
        val messages = mutableListOf<String>()
        var status = HealthStatus.HEALTHY

        device.reallocatedSectors?.let {
            if (it > 100) {
                status = HealthStatus.CRITICAL
                messages.add("High reallocated sectors: $it")
            } else if (it > 0) {
                status = maxOfStatus(status, HealthStatus.WARNING)
                messages.add("Reallocated sectors: $it")
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

class SimpleSsdHealthAnalyzer : HealthAnalyzer<StorageDevice.SataSsd> {
    override fun analyze(device: StorageDevice.SataSsd): DeviceHealth {
        val msgs = mutableListOf<String>()
        var status = HealthStatus.HEALTHY

        device.percentageUsed?.let {
            // percent used meaning depends on vendor (often "life left" vs used). we assume 0..100 used %
            if (it >= 95) {
                status = HealthStatus.WARNING
                msgs.add("SSD near rated endurance: $it%")
            }
            if (it >= 100) {
                status = HealthStatus.CRITICAL
                msgs.add("SSD reports 100% life used")
            }
        }

        device.uncorrectableErrors?.let {
            if (it > 0) {
                status = HealthStatus.CRITICAL
                msgs.add("Uncorrectable errors: $it - risk of data loss")
            }
        }

        device.totalWriteGiB?.let {
            msgs.add("Total writes: ${it} GiB")
        }

        if (msgs.isEmpty()) msgs.add("No immediate SSD SMART warnings detected")
        return DeviceHealth(status, msgs)
    }
}

class SimpleNvmeHealthAnalyzer : HealthAnalyzer<StorageDevice.Nvme> {
    override fun analyze(device: StorageDevice.Nvme): DeviceHealth {
        val msgs = mutableListOf<String>()
        var status = HealthStatus.HEALTHY

        device.percentageUsed?.let {
            if (it >= 95) {
                msgs.add("NVMe near rated endurance: $it%")
                status = HealthStatus.WARNING
            }
            if (it >= 100) {
                msgs.add("NVMe reports 100% life used")
                status = HealthStatus.CRITICAL
            }
        }

        device.mediaErrors?.let {
            if (it > 0) {
                msgs.add("Media errors: $it")
                status = HealthStatus.CRITICAL
            }
        }

        device.numErrLogEntries?.let {
            if (it > 0) msgs.add("Error log entries: $it")
        }

        if (msgs.isEmpty()) msgs.add("No immediate NVMe SMART warnings detected")
        return DeviceHealth(status, msgs)
    }
}