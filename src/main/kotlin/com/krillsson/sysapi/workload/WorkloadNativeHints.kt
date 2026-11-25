package com.krillsson.sysapi.workload

import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandInput
import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandOutputFailed
import com.krillsson.sysapi.graphql.mutations.PerformPowerCommandOutputStarted
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    WorkloadPlatform::class,
    RunState::class,
    PlatformAvailability::class,
    WorkloadList::class,
    WorkloadUsage::class,
    PowerCommand::class,
    PowerCommandRunning::class,
    PowerCommandSucceeded::class,
    PowerCommandFailed::class,
    PowerCommandJob::class,
    PowerCommandJobState::class,
    PerformPowerCommandInput::class,
    PerformPowerCommandOutputStarted::class,
    PerformPowerCommandOutputFailed::class,
)
class WorkloadNativeHints
