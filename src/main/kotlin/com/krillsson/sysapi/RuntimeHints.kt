package com.krillsson.sysapi

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.command.InspectImageResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.Statistics
import com.krillsson.sysapi.config.MdnsConfiguration
import org.springframework.aot.hint.BindingReflectionHintsRegistrar
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import org.springframework.util.ClassUtils
import oshi.jna.common.Nvml
import java.lang.reflect.Type

class RuntimeHint : RuntimeHintsRegistrar {

    companion object {
        private const val DOCKER_MODEL_PACKAGE = "com.github.dockerjava.api.model"
    }

    private val bindingRegistrar = BindingReflectionHintsRegistrar()

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection()
            .registerType(
                MdnsConfiguration::class.java,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
            )
        registerNvmlHints(hints)
        registerDockerResponseHints(hints, classLoader)
    }

    /**
     * docker-java maps every API response onto plain beans that Jackson fills through their
     * no-arg constructor and private `@JsonProperty` fields. Without reflection metadata the
     * native image exposes neither, so `listContainersCmd` fails with
     * `InvalidDefinitionException: cannot deserialize from Object value (no delegate- or
     * property-based Creator)` and Docker support is dead in the native image.
     *
     * The entry points are the four responses the agent reads — `listContainersCmd`,
     * `inspectContainerCmd`, `inspectImageCmd` and `statsCmd`. Walking their properties is not
     * enough on its own:
     * the registrar stops at array types, and docker-java holds a lot of the response in arrays
     * (`Container.ports`, `HostConfig.binds`, …), so the whole model package goes in as well.
     * That also keeps the metadata correct across docker-java upgrades. `logContainerCmd` needs
     * nothing: frames arrive over a raw stream that never reaches Jackson.
     */
    private fun registerDockerResponseHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val types: List<Type> = listOf(
            Container::class.java,
            InspectContainerResponse::class.java,
            InspectImageResponse::class.java,
            Statistics::class.java
        ) + dockerModelTypes(classLoader)
        bindingRegistrar.registerReflectionHints(hints.reflection(), *types.toTypedArray())
    }

    private fun dockerModelTypes(classLoader: ClassLoader?): List<Class<*>> {
        val resolver = PathMatchingResourcePatternResolver(classLoader)
        val metadataReaderFactory = CachingMetadataReaderFactory(resolver)
        val pattern = "classpath*:${ClassUtils.convertClassNameToResourcePath(DOCKER_MODEL_PACKAGE)}/**/*.class"
        return resolver.getResources(pattern).mapNotNull { resource ->
            val className = metadataReaderFactory.getMetadataReader(resource).classMetadata.className
            runCatching { ClassUtils.forName(className, classLoader) }.getOrNull()
        }
    }

    /**
     * OSHI probes NVIDIA GPUs through a JNA binding, which builds a JDK proxy over
     * [Nvml.NvmlLibrary]. OSHI guards the library load against `UnsatisfiedLinkError`, but an
     * unregistered proxy fails with `MissingReflectionRegistrationError` instead, which escapes
     * that guard and aborts startup on every Linux host that has a graphics card.
     */
    private fun registerNvmlHints(hints: RuntimeHints) {
        hints.proxies().registerJdkProxy(Nvml.NvmlLibrary::class.java)
        hints.reflection().registerType(
            Nvml.NvmlLibrary::class.java,
            MemberCategory.INVOKE_PUBLIC_METHODS
        )
        listOf(
            Nvml.NvmlMemory::class.java,
            Nvml.NvmlUtilization::class.java,
            Nvml.NvmlPciInfo::class.java
        ).forEach { structure ->
            hints.reflection().registerType(
                structure,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            )
        }
    }
}
