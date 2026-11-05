package com.krillsson.sysapi.graphql

import graphql.language.ObjectTypeDefinition
import graphql.parser.Parser
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

class SchemaObjectMethodFieldsTest {

    @Test
    fun `only fields with an explicit resolver are named after a no-argument Object method`() {
        // Given
        val objectMethods = Any::class.java.methods.filter { it.parameterCount == 0 }.map { it.name }.toSet()
        val schemaFiles = PathMatchingResourcePatternResolver().getResources("classpath:graphql/*.graphqls")

        // When
        val collisions = schemaFiles
            .flatMap { Parser().parseDocument(it.getContentAsString(Charsets.UTF_8)).getDefinitionsOfType(ObjectTypeDefinition::class.java) }
            .flatMap { type -> type.fieldDefinitions.map { type.name to it.name } }
            .filter { (_, field) -> field in objectMethods }
            .toSet()

        // Then
        collisions shouldBe setOf("ContainerUpdateCheckSettings" to "notify")
    }
}
