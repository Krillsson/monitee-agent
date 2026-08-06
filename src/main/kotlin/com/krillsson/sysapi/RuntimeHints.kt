package com.krillsson.sysapi

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.command.InspectImageResponse
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.core.command.ConnectToNetworkCmdImpl
import com.github.dockerjava.core.command.CreateContainerCmdImpl
import com.github.dockerjava.core.command.DisconnectFromNetworkCmdImpl
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
        private const val PAHO_JSR47_LOGGER = "org.eclipse.paho.client.mqttv3.logging.JSR47Logger"
        private const val PAHO_LOGCAT_BUNDLE = "org.eclipse.paho.client.mqttv3.internal.nls.logcat"
        private const val PAHO_MESSAGES_BUNDLE = "org.eclipse.paho.client.mqttv3.internal.nls.messages"
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
        registerDockerJsonHints(hints, classLoader)
        registerPahoHints(hints)
    }

    /**
     * Paho picks its logger with `Class.forName` and reads every log message it writes out of a
     * resource bundle, both of which the native image drops. The lookup is guarded against
     * `Exception`, but an unregistered class surfaces as a `MissingReflectionRegistrationError`
     * that escapes the guard, so creating an `MqttAsyncClient` fails outright.
     */
    private fun registerPahoHints(hints: RuntimeHints) {
        hints.reflection().registerTypeIfPresent(
            null,
            PAHO_JSR47_LOGGER,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS
        )
        hints.resources().registerResourceBundle(PAHO_LOGCAT_BUNDLE)
        hints.resources().registerResourceBundle(PAHO_MESSAGES_BUNDLE)
    }

    /**
     * docker-java maps every API response onto plain beans that Jackson fills through their
     * no-arg constructor and private `@JsonProperty` fields. Without reflection metadata the
     * native image exposes neither, so `listContainersCmd` fails with
     * `InvalidDefinitionException: cannot deserialize from Object value (no delegate- or
     * property-based Creator)` and Docker support is dead in the native image.
     *
     * The entry points are the responses the agent reads — `listContainersCmd`,
     * `inspectContainerCmd`, `inspectImageCmd`, `statsCmd` and `createContainerCmd`. Walking their
     * properties is not enough on its own:
     * the registrar stops at array types, and docker-java holds a lot of the response in arrays
     * (`Container.ports`, `HostConfig.binds`, …), so the whole model package goes in as well.
     * That also keeps the metadata correct across docker-java upgrades. `logContainerCmd` needs
     * nothing: frames arrive over a raw stream that never reaches Jackson.
     *
     * The commands that create a container or attach it to a network are themselves the request
     * body Jackson writes, so they need the same treatment as the responses.
     */
    private fun registerDockerJsonHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val types: List<Type> = listOf(
            Container::class.java,
            InspectContainerResponse::class.java,
            InspectImageResponse::class.java,
            Statistics::class.java,
            CreateContainerResponse::class.java,
            CreateContainerCmdImpl::class.java,
            CreateContainerCmdImpl.NetworkingConfig::class.java,
            ConnectToNetworkCmdImpl::class.java,
            DisconnectFromNetworkCmdImpl::class.java
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
