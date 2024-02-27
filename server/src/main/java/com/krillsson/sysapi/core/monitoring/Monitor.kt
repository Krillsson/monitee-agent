package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.monitor.MonitoredValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import java.util.*

abstract class Monitor<out T : MonitoredValue> {
    abstract val id: UUID
    abstract val type: Type
    abstract val config: MonitorConfig<out T>

    enum class Type(val valueType: ValueType) {
        CPU_LOAD(ValueType.Fractional),
        LOAD_AVERAGE_ONE_MINUTE(ValueType.Fractional),
        LOAD_AVERAGE_FIVE_MINUTES(ValueType.Fractional),
        LOAD_AVERAGE_FIFTEEN_MINUTES(ValueType.Fractional),
        CPU_TEMP(ValueType.Numerical),
        DRIVE_SPACE(ValueType.Numerical),
        FILE_SYSTEM_SPACE(ValueType.Numerical),
        DRIVE_READ_RATE(ValueType.Numerical),
        DISK_READ_RATE(ValueType.Numerical),
        DRIVE_WRITE_RATE(ValueType.Numerical),
        DISK_WRITE_RATE(ValueType.Numerical),
        MEMORY_SPACE(ValueType.Numerical),
        NETWORK_UP(ValueType.Conditional),
        NETWORK_UPLOAD_RATE(ValueType.Numerical),
        NETWORK_DOWNLOAD_RATE(ValueType.Numerical),
        CONTAINER_RUNNING(ValueType.Conditional),
        CONTAINER_MEMORY_SPACE(ValueType.Numerical),
        CONTAINER_CPU_LOAD(ValueType.Fractional),
        PROCESS_MEMORY_SPACE(ValueType.Numerical),
        PROCESS_CPU_LOAD(ValueType.Fractional),
        PROCESS_EXISTS(ValueType.Conditional),
        CONNECTIVITY(ValueType.Conditional),
        EXTERNAL_IP_CHANGED(ValueType.Conditional)
    }

    enum class ValueType {
        Numerical,
        Fractional,
        Conditional
    }

    abstract fun selectValue(event: MonitorInput): T?
    abstract fun maxValue(info: SystemInfo): T?
    abstract fun isPastThreshold(value: @UnsafeVariance T): Boolean
}