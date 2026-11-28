package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.event.EventSeverity
import com.krillsson.sysapi.core.monitoring.event.EventStore
import com.krillsson.sysapi.graphql.mutations.AcknowledgeEventInput
import com.krillsson.sysapi.graphql.mutations.AcknowledgeEventOutputFailed
import com.krillsson.sysapi.graphql.mutations.AcknowledgeEventOutputSucceeded
import com.krillsson.sysapi.graphql.mutations.UnacknowledgeEventInput
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    EventStore.StoredEvent::class,
    EventSeverity::class,
    MonitorStore.StoredMonitor::class,
    AcknowledgeEventInput::class,
    UnacknowledgeEventInput::class,
    AcknowledgeEventOutputSucceeded::class,
    AcknowledgeEventOutputFailed::class,
)
class MonitoringNativeHints
