package com.krillsson.sysapi.config

import org.springframework.boot.context.properties.bind.AbstractBindHandler
import org.springframework.boot.context.properties.bind.BindContext
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.source.ConfigurationPropertyName

class BoundNames : AbstractBindHandler() {

    private val exact = mutableSetOf<ConfigurationPropertyName>()
    private val maps = mutableSetOf<ConfigurationPropertyName>()

    override fun onSuccess(
        name: ConfigurationPropertyName,
        target: Bindable<*>,
        context: BindContext,
        result: Any
    ): Any {
        exact.add(name)
        if (result is Map<*, *>) {
            maps.add(name)
        }
        return result
    }

    fun unknown(keys: Collection<String>): List<String> =
        keys.filter { key ->
            val name = ConfigurationPropertyName.adapt(key, '.')
            name !in exact && maps.none { it.isAncestorOf(name) }
        }.distinct()
}
