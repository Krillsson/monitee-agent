package com.krillsson.sysapi.graphql

import SlowResolverWarningInstrumentation
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    SlowResolverWarningInstrumentation::class,
)
class GraphQlNativeHints
