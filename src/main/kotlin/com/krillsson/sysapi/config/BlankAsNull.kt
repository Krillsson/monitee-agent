package com.krillsson.sysapi.config

import org.springframework.boot.context.properties.bind.AbstractBindHandler
import org.springframework.boot.context.properties.bind.BindContext
import org.springframework.boot.context.properties.bind.BindHandler
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.source.ConfigurationPropertyName
import kotlin.reflect.full.primaryConstructor

class BlankAsNull(parent: BindHandler) : AbstractBindHandler(parent) {

    private val types = mutableMapOf<ConfigurationPropertyName, Class<*>?>()

    override fun <T : Any?> onStart(
        name: ConfigurationPropertyName,
        target: Bindable<T>,
        context: BindContext
    ): Bindable<T>? {
        types[name] = target.type.resolve()
        return super.onStart(name, target, context)
    }

    override fun onSuccess(
        name: ConfigurationPropertyName,
        target: Bindable<*>,
        context: BindContext,
        result: Any
    ): Any? {
        val bound = super.onSuccess(name, target, context, result)
        return when {
            bound is String && bound.isBlank() && isNullableParameter(name) -> null
            else -> bound
        }
    }

    private fun isNullableParameter(name: ConfigurationPropertyName): Boolean {
        val owner = types[name.parent]
        val key = uniform(name.getElement(name.numberOfElements - 1, ConfigurationPropertyName.Form.UNIFORM))
        return runCatching {
            owner?.kotlin?.primaryConstructor?.parameters
                ?.firstOrNull { uniform(it.name.orEmpty()) == key }
                ?.type?.isMarkedNullable
        }.getOrNull() == true
    }

    private fun uniform(value: String) = value.lowercase().filter { it.isLetterOrDigit() }
}
