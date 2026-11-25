package com.krillsson.sysapi.ups

import com.krillsson.sysapi.graphql.domain.UpsInfoUnavailable
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    UpsInfoUnavailable::class,
)
class UpsNativeHints
