package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.config.TemperatureUnit.*
import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.stereotype.Component
import java.util.*
import kotlin.math.roundToInt


enum class TemperatureUnit {
    Celsius,
    Fahrenheit
}

@Component
class TemperatureFormatter(yamlConfigFile: YAMLConfigFile) {

    companion object {
        private const val degrees = 0x00B0.toChar()
    }

    private val preferredTemperatureUnit = when (yamlConfigFile.formatting.temperatureUnit) {
        system -> readTemperatureUnitFromLocale()
        celsius -> TemperatureUnit.Celsius
        fahrenheit -> TemperatureUnit.Fahrenheit
    }

    fun format(celsius: Int): String {
        return when (preferredTemperatureUnit) {
            TemperatureUnit.Celsius -> "${celsius}${degrees}C"
            TemperatureUnit.Fahrenheit -> "${celsius.convertCelsiusToFahrenheit()}${degrees}F"
        }
    }

    fun convert(celsius: Int) = when (preferredTemperatureUnit) {
        TemperatureUnit.Celsius -> celsius
        TemperatureUnit.Fahrenheit -> celsius.convertCelsiusToFahrenheit()
    }

    private fun readTemperatureUnitFromLocale(): TemperatureUnit = if (Locale.getDefault().country.equals(
            "us",
            ignoreCase = true
        )
    ) TemperatureUnit.Fahrenheit else TemperatureUnit.Celsius

    fun Int.convertCelsiusToFahrenheit(): Int {
        return (this * 9.0f / 5.0f + 32).roundToInt()
    }
}
