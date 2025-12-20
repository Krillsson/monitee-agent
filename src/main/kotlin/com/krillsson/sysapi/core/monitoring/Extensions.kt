package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.smart.HealthStatus
import kotlin.enums.EnumEntries
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun Long.toNumericalValue() = MonitoredValue.NumericalValue(this)
fun Boolean.toConditionalValue() = MonitoredValue.ConditionalValue(this)
fun Float.toFractionalValue() = MonitoredValue.FractionalValue(this)
fun Float.toNumericalValue() = MonitoredValue.NumericalValue(this.toLong())
fun Int.toNumericalValue() = MonitoredValue.NumericalValue(this.toLong())
fun Double.toFractionalValue() = MonitoredValue.FractionalValue(this.toFloat())
fun Double.toConditionalValue() = if (this == 1.0) true.toConditionalValue() else false.toConditionalValue()

fun <E : Enum<E>> Double.toEnumFromDouble(entries: EnumEntries<E>): E = entries[this.roundToInt()]
fun <E : Enum<E>> String.toEnumFromString(entries: EnumEntries<E>): E? =
    entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

fun <E : Enum<E>> Double.toEnumValue(entries: EnumEntries<E>): MonitoredValue.EnumValue<E> =
    MonitoredValue.EnumValue(this.toEnumFromDouble(entries))

fun <E : Enum<E>> String.toEnumValueFromString(entries: EnumEntries<E>): MonitoredValue.EnumValue<E>? =
    this.toEnumFromString(entries)?.let { MonitoredValue.EnumValue(it) }

fun <E : Enum<E>> E.toEnumValue(): MonitoredValue.EnumValue<E> =
    MonitoredValue.EnumValue(this)

fun <E : Enum<E>> Monitor.Type.toEnumEntries(): EnumEntries<E>? =
    when (this) {
        Monitor.Type.DISK_SMART_HEALTH -> HealthStatus.entries as EnumEntries<E>
        Monitor.Type.CPU_LOAD,
        Monitor.Type.LOAD_AVERAGE_ONE_MINUTE,
        Monitor.Type.LOAD_AVERAGE_FIVE_MINUTES,
        Monitor.Type.LOAD_AVERAGE_FIFTEEN_MINUTES,
        Monitor.Type.CPU_TEMP,
        Monitor.Type.FILE_SYSTEM_SPACE,
        Monitor.Type.DISK_READ_RATE,
        Monitor.Type.DISK_TEMPERATURE,
        Monitor.Type.DISK_WRITE_RATE,
        Monitor.Type.MEMORY_SPACE,
        Monitor.Type.MEMORY_USED,
        Monitor.Type.NETWORK_UP,
        Monitor.Type.NETWORK_UPLOAD_RATE,
        Monitor.Type.NETWORK_DOWNLOAD_RATE,
        Monitor.Type.CONTAINER_RUNNING,
        Monitor.Type.CONTAINER_MEMORY_SPACE,
        Monitor.Type.CONTAINER_CPU_LOAD,
        Monitor.Type.PROCESS_MEMORY_SPACE,
        Monitor.Type.PROCESS_CPU_LOAD,
        Monitor.Type.PROCESS_EXISTS,
        Monitor.Type.CONNECTIVITY,
        Monitor.Type.WEBSERVER_UP,
        Monitor.Type.EXTERNAL_IP_CHANGED,
        Monitor.Type.UPS_OPERATING_NORMALLY,
        Monitor.Type.UPS_LOAD_PERCENTAGE -> null
    }


fun Double.toNumericalValue() = this.roundToLong().toNumericalValue()