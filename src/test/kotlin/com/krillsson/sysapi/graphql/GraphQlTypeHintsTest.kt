package com.krillsson.sysapi.graphql

import graphql.language.InterfaceTypeDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.UnionTypeDefinition
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.aot.hint.BindingReflectionHintsRegistrar
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.util.ClassUtils
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.lang.reflect.Modifier
import java.lang.reflect.Type

class GraphQlTypeHintsTest {

    private val classLoader = javaClass.classLoader

    private val applicationClasses: List<Class<*>> by lazy {
        val scanner = object : ClassPathScanningCandidateComponentProvider(false) {
            override fun isCandidateComponent(beanDefinition: AnnotatedBeanDefinition) = true
        }
        scanner.addIncludeFilter(AssignableTypeFilter(Any::class.java))
        scanner.findCandidateComponents("com.krillsson.sysapi")
            .mapNotNull(BeanDefinition::getBeanClassName)
            .map { ClassUtils.forName(it, classLoader) }
    }

    private val hintedClasses: Set<Class<*>> by lazy {
        applicationClasses
            .mapNotNull { AnnotatedElementUtils.findMergedAnnotation(it, RegisterReflectionForBinding::class.java) }
            .flatMap { it.classes.toList() }
            .map { it.java }
            .toSet()
    }

    private val tracedClassNames: Set<String> by lazy {
        val nameEntry = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
        PathMatchingResourcePatternResolver(classLoader).getResources("classpath*:META-INF/native-image/**/reflect-config.json")
            .map { it.inputStream.bufferedReader().readText() }
            .flatMap { json -> nameEntry.findAll(json).map { it.groupValues[1] }.toList() }
            .toSet()
    }

    private val controllerReturnTypes: List<Type> by lazy {
        applicationClasses
            .filter { AnnotatedElementUtils.hasAnnotation(it, Controller::class.java) }
            .flatMap { it.declaredMethods.asList() }
            .filter { AnnotatedElementUtils.hasAnnotation(it, SchemaMapping::class.java) }
            .map { it.genericReturnType }
    }

    private val boundHints: RuntimeHints by lazy {
        val types: List<Type> = hintedClasses.toList() + controllerReturnTypes
        RuntimeHints().also { hints ->
            BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(), *types.toTypedArray())
        }
    }

    private fun hasNoInstanceState(type: Class<*>) = type.declaredFields.all { Modifier.isStatic(it.modifiers) }

    private fun isCovered(type: Class<*>) = RuntimeHintsPredicates.reflection().onType(type).test(boundHints) ||
        type.name in tracedClassNames ||
        hasNoInstanceState(type)

    private fun schemaRegistry(): TypeDefinitionRegistry {
        val registry = TypeDefinitionRegistry()
        PathMatchingResourcePatternResolver(classLoader).getResources("classpath*:graphql/*.graphqls")
            .map { it.inputStream.bufferedReader().readText() }
            .forEach { registry.merge(SchemaParser().parse(it)) }
        return registry
    }

    private fun polymorphicMemberNames(registry: TypeDefinitionRegistry): Set<String> {
        val interfaces = registry.getTypes(InterfaceTypeDefinition::class.java).map { it.name }.toSet()
        val unionMembers = registry.getTypes(UnionTypeDefinition::class.java)
            .flatMap { union -> union.memberTypes.map { (it as graphql.language.TypeName).name } }
        val implementations = registry.getTypes(ObjectTypeDefinition::class.java)
            .filter { type -> type.implements.any { (it as graphql.language.TypeName).name in interfaces } }
            .map { it.name }
        return (unionMembers + implementations).toSet()
    }

    @Test
    fun `finds union members and interface implementations in the schema`() {
        // Given
        val registry = schemaRegistry()

        // When
        val members = polymorphicMemberNames(registry)

        // Then
        members.shouldNotBeEmpty()
    }

    @Test
    fun `every union member and interface implementation backed by a class of the same name is covered by a reflection hint, a controller return type, the checked-in reflect-config or has no state to read`() {
        // Given
        val members = polymorphicMemberNames(schemaRegistry())
        val classesByName = applicationClasses.groupBy { it.simpleName }

        // When
        val unhinted = members
            .filter { name -> classesByName[name].orEmpty().isNotEmpty() }
            .filter { name -> classesByName.getValue(name).none(::isCovered) }
            .sorted()

        // Then
        unhinted shouldBe emptyList()
    }
}
