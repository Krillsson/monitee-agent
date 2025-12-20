package com.krillsson.sysapi.core.monitoring

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
