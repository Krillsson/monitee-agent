package com.krillsson.sysapi.mqtt

data class MqttEntity(
    val key: String,
    val name: String,
    val component: Component,
    val unit: String? = null,
    val deviceClass: String? = null,
    val stateClass: String? = null,
    val precision: Int? = null,
    val diagnostic: Boolean = false,
    val enabledByDefault: Boolean = true
) {
    enum class Component(val id: String) {
        SENSOR("sensor"),
        BINARY_SENSOR("binary_sensor")
    }
}

data class MeasuredEntity(
    val entity: MqttEntity,
    val value: Any?,
    val attributes: Map<String, Any?>? = null
)

object MqttUnits {
    const val PERCENT = "%"
    const val CELSIUS = "°C"
    const val BYTES = "B"
    const val BYTES_PER_SECOND = "B/s"
    const val SECONDS = "s"
    const val WATT = "W"
}

object MqttKeys {

    private val separators = Regex("[^a-z0-9]+")

    fun slug(value: String): String =
        value.lowercase().replace(separators, "_").trim('_').ifEmpty { "unknown" }

    fun <T> uniqueSlugs(items: List<T>, name: (T) -> String): List<Pair<String, T>> {
        val taken = mutableSetOf<String>()
        return items.map { item ->
            val base = slug(name(item))
            var candidate = base
            var suffix = 2
            while (!taken.add(candidate)) {
                candidate = "${base}_$suffix"
                suffix++
            }
            candidate to item
        }
    }
}
