package com.krillsson.sysapi.unraid

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    UnraidApi::class,
    UnraidApi.VmDomain::class,
    UnraidVm::class,
    UnraidVmState::class,
)
class UnraidNativeHints
