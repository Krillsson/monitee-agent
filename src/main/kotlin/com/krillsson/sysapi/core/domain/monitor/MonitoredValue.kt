package com.krillsson.sysapi.core.domain.monitor

import kotlin.math.roundToLong

sealed class MonitoredValue {
    data class NumericalValue(
        val value: Long
    ) : MonitoredValue() {
        operator fun compareTo(other: NumericalValue) = this.value.compareTo(other.value)
    }

    data class FractionalValue(
        val value: Float
    ) : MonitoredValue() {
        operator fun compareTo(other: FractionalValue) = this.value.compareTo(other.value)
    }

    data class ConditionalValue(
        val value: Boolean
    ) : MonitoredValue()

    data class EnumValue<E : Enum<E>>(
        val value: E
    ) : MonitoredValue()
}

fun Long.toNumericalValue() = MonitoredValue.NumericalValue(this)
fun Boolean.toConditionalValue() = MonitoredValue.ConditionalValue(this)
fun Float.toFractionalValue() = MonitoredValue.FractionalValue(this)
fun Float.toNumericalValue() = MonitoredValue.NumericalValue(this.toLong())
fun Int.toNumericalValue() = MonitoredValue.NumericalValue(this.toLong())
fun Double.toFractionalValue() = MonitoredValue.FractionalValue(this.toFloat())
fun Double.toConditionalValue() = if (this == 1.0) true.toConditionalValue() else false.toConditionalValue()
fun Double.toNumericalValue() = this.roundToLong().toNumericalValue()
