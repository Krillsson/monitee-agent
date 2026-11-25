package com.krillsson.sysapi.core.history.series

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    MetricHistory::class,
    MetricHistoryPoint::class,
    MetricTierAvailability::class,
    HistoryResolution::class,
    MetricId::class,
)
class MetricSeriesNativeHints
