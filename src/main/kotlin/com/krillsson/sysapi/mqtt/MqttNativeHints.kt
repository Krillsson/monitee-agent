package com.krillsson.sysapi.mqtt

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    MqttEventPayload::class,
)
class MqttNativeHints
