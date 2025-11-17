package com.krillsson.sysapi.smart

sealed class StorageDevice(
    open val name: String,
    open val temperatureCelsius: Int?,
    open val powerOnHours: Long?,
    open val powerCycleCount: Long?,
    open val rawAttributes: Map<Int, SmartAttribute>
) {

    data class SmartAttribute(
        val id: Int,
        val name: String,
        val raw: Long?,
        val normalized: Int?,
        val worst: Int?
    )

    data class Hdd(
        override val name: String,
        override val temperatureCelsius: Int?,
        override val powerOnHours: Long?,
        override val powerCycleCount: Long?,
        override val rawAttributes: Map<Int, SmartAttribute>,

        val reallocatedSectors: Long?,
        val pendingSectors: Long?,
        val uncorrectableSectors: Long?,
        val offlineUncorrectable: Long?,
        val spinRetryCount: Long?,
        val seekErrorRate: Long?,
        val udmaCrcErrors: Long?,
    ) : StorageDevice(name, temperatureCelsius, powerOnHours, powerCycleCount, rawAttributes)

    data class Nvme(
        override val name: String,
        override val temperatureCelsius: Int?,
        override val powerOnHours: Long?,
        override val powerCycleCount: Long?,
        override val rawAttributes: Map<Int, SmartAttribute>, // may be empty

        val percentageUsed: Int?,
        val dataUnitsRead: Long?,
        val dataUnitsWritten: Long?,
        val mediaErrors: Long?,
        val numErrLogEntries: Long?,
        val unsafeShutdowns: Long?,
        val controllerBusyTimeMinutes: Long?,
        val vendorData: Map<String, Any>
    ) : StorageDevice(name, temperatureCelsius, powerOnHours, powerCycleCount, rawAttributes)

    data class SataSsd(
        override val name: String,
        override val temperatureCelsius: Int?,
        override val powerOnHours: Long?,
        override val powerCycleCount: Long?,
        override val rawAttributes: Map<Int, SmartAttribute>,

        // Wear / Endurance
        val percentageUsed: Int?,
        val wearLevelingCount: Int?,
        val availableReservedSpace: Int?,

        // NAND Writes
        val totalWriteGiB: Long?,
        val totalReadGiB: Long?,

        // Errors
        val mediaErrors: Long?,
        val uncorrectableErrors: Long?,
        val udmaCrcErrors: Long?
    ) : StorageDevice(name, temperatureCelsius, powerOnHours, powerCycleCount, rawAttributes)
}