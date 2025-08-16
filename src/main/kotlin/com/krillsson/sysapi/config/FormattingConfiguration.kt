package com.krillsson.sysapi.config

data class FormattingConfiguration(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.system,
)

enum class TemperatureUnit {
    system,
    celsius,
    fahrenheit
}