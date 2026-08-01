package com.krillsson.sysapi

import com.krillsson.sysapi.config.MdnsConfiguration
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import oshi.jna.common.Nvml

class RuntimeHint : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection()
            .registerType(
                MdnsConfiguration::class.java,
                MemberCategory.PUBLIC_FIELDS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS
            )
        registerNvmlHints(hints)
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
