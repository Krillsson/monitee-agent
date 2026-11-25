package com.krillsson.sysapi.core.genericevents

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    GenericEventStore.StoredGenericEvent.UpdateAvailable::class,
    GenericEventStore.StoredGenericEvent.MonitoredItemMissing::class,
    GenericEventStore.StoredGenericEvent.ContainerImageUpdateAvailable::class,
    ContainerImageUpdateAvailable::class,
)
class GenericEventsNativeHints
