package com.krillsson.sysapi.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.SystemEnvironmentPropertySource
import org.springframework.core.io.ByteArrayResource

fun configFileParser(): ObjectMapper = ObjectMapper(YAMLFactory())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .findAndRegisterModules()

fun yamlSource(yaml: String): PropertySource<*> =
    YamlPropertySourceLoader().load("configuration.yml", ByteArrayResource(yaml.toByteArray())).single()

fun bind(
    yaml: String,
    environment: Map<String, Any> = emptyMap(),
    handler: BoundNames = BoundNames()
): YAMLConfigFile {
    val sources = MutablePropertySources()
    if (environment.isNotEmpty()) {
        sources.addFirst(SystemEnvironmentPropertySource("systemEnvironment", environment))
    }
    sources.addLast(yamlSource(yaml))
    return Binder(ConfigurationPropertySources.from(sources))
        .bind("", Bindable.of(YAMLConfigFile::class.java), handler)
        .get()
}

fun propertyNames(yaml: String): List<String> =
    (yamlSource(yaml) as MapPropertySource).propertyNames.toList()
